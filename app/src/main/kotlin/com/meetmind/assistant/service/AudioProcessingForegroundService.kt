// T031: Foreground service — audio capture + cloud inference routing
// Spec 004 will flesh out VAD/ASR; this file establishes the cloud integration points.
// T035 (spec 008): onTranscriptSegment() feeds TranscriptWindowBuffer for continuous analysis
// spec 009 — T022: @AndroidEntryPoint + @Inject replaces (application as MeetMindApplication).container
// spec 009 — T042: SttRepository injected; startRecording() ASR loop in onStartCommand()
package com.meetmind.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meetmind.assistant.analysis.TimestampedSegment
import com.meetmind.assistant.analysis.TranscriptWindowBuffer
import com.meetmind.assistant.data.ModelDownloadManager
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.SessionMode
import com.meetmind.assistant.data.model.SttModelConfig
import com.meetmind.assistant.inference.CloudInferenceEngine
import com.meetmind.assistant.stt.SttRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Long-running foreground service (foregroundServiceType = microphone|dataSync).
 *
 * Responsibilities:
 *  1. Capture microphone audio (spec 009 US1 — Sherpa-ONNX VAD + ASR wired in T042)
 *  2. Detect questions from transcript
 *  3. T031: Route detected questions to [CloudInferenceEngine] for suggestions
 *  4. Broadcast [InferenceEvent] stream to [SessionViewModel] via shared StateFlow
 *
 * The on-device audio pipeline (spec 009 US1) hooks into [onTranscriptSegment].
 */
@AndroidEntryPoint
class AudioProcessingForegroundService : Service() {

    companion object {
        private const val TAG = "AudioProcessingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "meetmind_session"
        private const val CHANNEL_NAME = "MeetMind Session"

        /**
         * Returns the directory where Parakeet TDT STT model files are stored.
         * Uses external files dir with fallback to internal storage.
         */
        fun sttModelDir(context: Context): File {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return File(base, SttModelConfig.MODEL_DIR_NAME)
        }

        /** Broadcast action used to relay InferenceEvents to the UI layer */
        const val ACTION_INFERENCE_EVENT = "com.meetmind.assistant.INFERENCE_EVENT"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_TEXT = "text"

        /**
         * T035 (spec 008): Rolling window fed to TranscriptWindowBuffer from ASR segments.
         * Matches the default windowSizeMs in AnalysisModule (60 s).
         */
        const val TRANSCRIPT_WINDOW_SIZE_MS = 60_000L
    }

    @Inject
    lateinit var cloudInferenceEngine: CloudInferenceEngine

    /**
     * T035 (spec 008): Shared buffer fed by every ASR segment; read by [AnalysisCadenceController]
     * on each cadence tick to derive the sliding analysis window.
     * Singleton provided by AnalysisModule — same instance as cadenceController uses.
     */
    @Inject
    lateinit var transcriptWindowBuffer: TranscriptWindowBuffer

    /**
     * spec 009 T042: Sherpa-ONNX STT pipeline.
     * [startRecording] emits [RecognitionResult] fragments which are forwarded to
     * [onTranscriptSegment] for continuous analysis.
     */
    @Inject
    lateinit var sttRepository: SttRepository

    @Inject
    lateinit var downloadManager: ModelDownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // spec 009 T042: Launch the STT capture loop.
        // initialize() loads the ONNX model if STT files are present; startRecording() begins
        // emitting RecognitionResult fragments which are forwarded to onTranscriptSegment().
        serviceScope.launch {
            val sttModelDir = sttModelDir(this@AudioProcessingForegroundService)
            if (!downloadManager.sttModelsReady(sttModelDir)) {
                Log.w(TAG, "STT models not present at $sttModelDir — skipping ASR start")
                return@launch
            }
            try {
                sttRepository.initialize(sttModelDir.absolutePath)
                sttRepository.startRecording()
                    .collect { result ->
                        if (result.isComplete) {
                            onTranscriptSegment(result.text)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "STT pipeline error", e)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.launch {
            try {
                sttRepository.stopRecording()
                sttRepository.releaseModel()
            } catch (e: Exception) {
                Log.w(TAG, "STT cleanup on destroy failed", e)
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * T035 (spec 008): Called by the ASR pipeline for every recognised speech segment.
     *
     * Appends the segment to [TranscriptWindowBuffer] so the continuous analysis cadence
     * (spec 008) always operates on up-to-date conversation text.
     *
     * @param text        Recognised text from the ASR engine (Sherpa-ONNX / on-device)
     * @param timestampMs Wall-clock milliseconds at the time of recognition (defaults to now)
     */
    fun onTranscriptSegment(
        text: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (text.isBlank()) return
        transcriptWindowBuffer.append(
            segment = TimestampedSegment(timestampMs = timestampMs, text = text),
            windowSizeMs = TRANSCRIPT_WINDOW_SIZE_MS
        )
    }

    /**
     * Called by the question detection pipeline when a question is detected.
     * T031: Routes the question to [CloudInferenceEngine]; emits [InferenceEvent]s.
     */
    fun onQuestionDetected(
        questionText: String,
        systemPrompt: String,
        provider: CloudProvider,
        sessionMode: SessionMode
    ) {
        val request = CloudInferenceRequest(
            questionText = questionText,
            systemPrompt = systemPrompt,
            provider = provider,
            sessionMode = sessionMode
        )

        serviceScope.launch {
            cloudInferenceEngine.streamSuggestion(request)
                .onEach { event -> broadcastInferenceEvent(event) }
                .launchIn(this)
        }
    }

    private fun broadcastInferenceEvent(event: InferenceEvent) {
        val intent = Intent(ACTION_INFERENCE_EVENT).apply {
            setPackage(packageName)
            when (event) {
                is InferenceEvent.Token -> {
                    putExtra(EXTRA_EVENT_TYPE, "token")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.text)
                }
                is InferenceEvent.Complete -> {
                    putExtra(EXTRA_EVENT_TYPE, "complete")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.fullText)
                }
                is InferenceEvent.FallbackActivated -> {
                    putExtra(EXTRA_EVENT_TYPE, "fallback")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.reason.name)
                }
                is InferenceEvent.Error -> {
                    putExtra(EXTRA_EVENT_TYPE, "error")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.message)
                }
            }
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeetMind is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
