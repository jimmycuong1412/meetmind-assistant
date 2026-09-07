package com.meetmind.assistant.debug

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * **Throwaway probe — Task 0 of the Interview Mode plan. Debug builds only.**
 *
 * Answers one question, on real hardware, before any capture plumbing is written:
 *
 * > Can this app capture the far-end audio of a Zoom / Meet / Teams call via
 * > `AudioPlaybackCapture`?
 *
 * The whole mic-vs-loopback channel-split design (spec §3.1) rests on that being
 * true, and Android may refuse it: an app can opt out of playback capture with
 * `ALLOW_CAPTURE_BY_NONE`, and `USAGE_VOICE_COMMUNICATION` audio — how conferencing
 * apps commonly route call audio — is generally not capturable at all. A negative
 * result here redirects the work to the USB line-in tap (plan Task 3b) and saves
 * building Tasks 3/4 on a false premise.
 *
 * ## How to run
 * ```
 * adb shell am start -n com.meetmind.assistant/com.meetmind.assistant.debug.PlaybackCaptureProbeActivity
 * adb logcat -s PlaybackProbe
 * ```
 * Tap **Start capture**, approve the system consent dialog, then join a call on the
 * laptop and have the far end speak. Watch the RMS/dBFS readout on screen or in
 * logcat.
 *
 * ## Reading the result
 * - **dBFS rises well above the silence floor when the far end speaks** → capture
 *   works; proceed with Tasks 3/4.
 * - **dBFS pinned near the floor (~-90 dB) while audio is clearly playing** → this
 *   app's audio is not capturable; pivot to Task 3b.
 *
 * Test each of Zoom, Meet, Teams, and a browser call separately — they differ, and
 * one working does not imply another does.
 *
 * ## Deliberate limitations
 * Not production code, and not meant to become any. It writes no files, keeps no
 * state, and lives in `src/debug` so it cannot ship in a release build. It captures
 * `USAGE_MEDIA` / `USAGE_UNKNOWN` / `USAGE_GAME` — the only usages the platform
 * permits; `USAGE_VOICE_COMMUNICATION` cannot be added, which is precisely the
 * limitation under test.
 */
class PlaybackCaptureProbeActivity : ComponentActivity() {

    private companion object {
        const val TAG = "PlaybackProbe"

        /** Must match the STT pipeline so the probe measures a representative stream. */
        const val SAMPLE_RATE_HZ = 16_000

        /** One RMS report per second. */
        const val REPORT_INTERVAL_MS = 1_000L

        /**
         * dBFS below which a window is treated as silence. Digital silence is -inf;
         * real captures floor out around -80..-90 dB from dither and noise.
         */
        const val SILENCE_FLOOR_DB = -70.0

        /** How long to wait for the projection foreground service to reach running state. */
        const val SERVICE_START_TIMEOUT_MS = 3_000L
        const val SERVICE_POLL_INTERVAL_MS = 50L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var captureJob: Job? = null
    private var projection: MediaProjection? = null

    // ─── UI state ────────────────────────────────────────────────────────────
    private var status by mutableStateOf("Idle — tap Start capture")
    private var currentDb by mutableStateOf(Double.NEGATIVE_INFINITY)
    private var peakDb by mutableStateOf(Double.NEGATIVE_INFINITY)
    private var reportCount by mutableStateOf(0)
    private var soundedCount by mutableStateOf(0)
    private var isCapturing by mutableStateOf(false)

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            status = "DENIED — consent refused. Playback capture cannot run."
            Log.w(TAG, "MediaProjection consent denied")
            return@registerForActivityResult
        }

        // Android 14+ requires a RUNNING foreground service of type
        // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION before getMediaProjection() may be
        // called; otherwise it throws SecurityException. Starting the service is
        // asynchronous, so we must wait for it to actually reach the foreground state
        // rather than calling getMediaProjection() straight after startForegroundService().
        status = "Starting projection service…"
        ProbeProjectionService.start(this)

        scope.launch {
            val deadline = System.currentTimeMillis() + SERVICE_START_TIMEOUT_MS
            while (!ProbeProjectionService.isRunning && System.currentTimeMillis() < deadline) {
                delay(SERVICE_POLL_INTERVAL_MS)
            }
            if (!ProbeProjectionService.isRunning) {
                status = "ERROR — projection service did not start in time"
                Log.e(TAG, "ProbeProjectionService failed to reach foreground state")
                return@launch
            }

            // The projection token must be held while capturing; releasing it stops the stream.
            val mgr = getSystemService(MediaProjectionManager::class.java)
            projection = try {
                mgr.getMediaProjection(result.resultCode, result.data!!)
            } catch (e: SecurityException) {
                status = "ERROR — getMediaProjection refused: ${e.message}"
                Log.e(TAG, "getMediaProjection failed", e)
                ProbeProjectionService.stop(this@PlaybackCaptureProbeActivity)
                return@launch
            }
            startCapture()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProbeScreen(
                        status = status,
                        currentDb = currentDb,
                        peakDb = peakDb,
                        reportCount = reportCount,
                        soundedCount = soundedCount,
                        isCapturing = isCapturing,
                        onStart = ::requestConsent,
                        onStop = ::stopCapture
                    )
                }
            }
        }
    }

    private fun requestConsent() {
        resetCounters()
        status = "Requesting MediaProjection consent…"
        val mgr = getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun resetCounters() {
        currentDb = Double.NEGATIVE_INFINITY
        peakDb = Double.NEGATIVE_INFINITY
        reportCount = 0
        soundedCount = 0
    }

    private fun startCapture() {
        val mp = projection ?: run {
            status = "ERROR — no MediaProjection token"
            return
        }

        // Only these usages are capturable. USAGE_VOICE_COMMUNICATION is deliberately
        // absent because the platform does not allow capturing it — the exact
        // restriction this probe exists to detect.
        val config = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val record = try {
            @Suppress("MissingPermission") // RECORD_AUDIO is declared and granted for STT
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 4)
                .setAudioPlaybackCaptureConfig(config)
                .build()
        } catch (e: Exception) {
            status = "ERROR building AudioRecord: ${e.message}"
            Log.e(TAG, "AudioRecord build failed", e)
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            status = "ERROR — AudioRecord not initialized (state=${record.state})"
            record.release()
            return
        }

        isCapturing = true
        status = "CAPTURING — play far-end audio now"
        Log.i(TAG, "=== probe started: ${SAMPLE_RATE_HZ}Hz mono, minBuf=$minBuf ===")

        captureJob = scope.launch {
            withContext(Dispatchers.IO) {
                record.startRecording()
                val buf = ShortArray(SAMPLE_RATE_HZ) // 1 s window
                var windowStart = System.currentTimeMillis()
                var sumSquares = 0.0
                var sampleCount = 0L
                var peakAbs = 0

                try {
                    while (isActive) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) {
                            Log.w(TAG, "read() returned $n")
                            continue
                        }
                        for (i in 0 until n) {
                            val v = buf[i].toInt()
                            sumSquares += (v.toDouble() * v.toDouble())
                            if (abs(v) > peakAbs) peakAbs = abs(v)
                        }
                        sampleCount += n

                        val now = System.currentTimeMillis()
                        if (now - windowStart >= REPORT_INTERVAL_MS && sampleCount > 0) {
                            val rms = sqrt(sumSquares / sampleCount)
                            val db = if (rms <= 0.0) {
                                Double.NEGATIVE_INFINITY
                            } else {
                                20.0 * log10(rms / 32768.0)
                            }
                            val sounded = db > SILENCE_FLOOR_DB

                            withContext(Dispatchers.Main) {
                                currentDb = db
                                if (db > peakDb) peakDb = db
                                reportCount++
                                if (sounded) soundedCount++
                            }

                            Log.i(
                                TAG,
                                "RMS=%.1f rms_dbfs=%.1f peak_sample=%d %s".format(
                                    rms, db, peakAbs,
                                    if (sounded) "<<< AUDIO PRESENT" else "(silence)"
                                )
                            )

                            windowStart = now
                            sumSquares = 0.0
                            sampleCount = 0
                            peakAbs = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "capture loop error", e)
                    withContext(Dispatchers.Main) { status = "ERROR: ${e.message}" }
                } finally {
                    runCatching { record.stop() }
                    record.release()
                    Log.i(TAG, "=== probe stopped ===")
                }
            }
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        projection?.stop()
        projection = null
        ProbeProjectionService.stop(this)
        isCapturing = false
        status = verdict()
        Log.i(TAG, "VERDICT: ${status}")
    }

    /** Summarize the run so the result is unambiguous without reading the log. */
    private fun verdict(): String = when {
        reportCount == 0 -> "No data captured — probe ran too briefly."
        soundedCount == 0 ->
            "NOT CAPTURABLE — $reportCount windows, all silent. " +
                "If audio was definitely playing, this app's audio cannot be captured. " +
                "Pivot to the USB line-in tap (plan Task 3b)."
        else ->
            "CAPTURABLE — audio present in $soundedCount of $reportCount windows " +
                "(peak %.1f dBFS). Playback capture works for this app.".format(peakDb)
    }

    override fun onDestroy() {
        super.onDestroy()
        captureJob?.cancel()
        projection?.stop()
        ProbeProjectionService.stop(this)
        scope.cancel()
    }
}

@Composable
private fun ProbeScreen(
    status: String,
    currentDb: Double,
    peakDb: Double,
    reportCount: Int,
    soundedCount: Int,
    isCapturing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Playback Capture Probe", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Task 0 — can this app capture far-end call audio?",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(status, style = MaterialTheme.typography.bodyLarge)

        // Map -80..0 dBFS onto a 0..1 bar so movement is visible at a glance.
        val level = if (currentDb.isFinite()) {
            ((currentDb + 80.0) / 80.0).coerceIn(0.0, 1.0).toFloat()
        } else 0f

        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Text(
            text = "now: ${fmtDb(currentDb)}    peak: ${fmtDb(peakDb)}",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "windows: $reportCount    with audio: $soundedCount",
            style = MaterialTheme.typography.bodyMedium
        )

        if (isCapturing) {
            Button(onClick = onStop) { Text("Stop & show verdict") }
        } else {
            Button(onClick = onStart) { Text("Start capture") }
        }

        Text(
            "1. Tap Start and approve the consent dialog.\n" +
                "2. Join a call on the laptop; have the far end speak.\n" +
                "3. Watch for \"AUDIO PRESENT\". Then Stop for a verdict.\n" +
                "4. Repeat per app — Zoom, Meet, Teams, browser.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun fmtDb(db: Double): String =
    if (db.isFinite()) "%.1f dBFS".format(db) else "—"
