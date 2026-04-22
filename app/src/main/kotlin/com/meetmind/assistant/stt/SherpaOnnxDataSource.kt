// spec 009 — T039: Sherpa-ONNX STT data source
// Adapted from F:\Git\HearoPilot-App\feature-stt\src\main\java\com\hearopilot\app\feature\stt\datasource\SherpaOnnxDataSource.kt
// Package changed to com.meetmind.assistant.stt; imports updated to local sherpa wrappers.
package com.meetmind.assistant.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.meetmind.assistant.data.model.RecognitionResult
import com.meetmind.assistant.stt.sherpa.FeatureConfig
import com.meetmind.assistant.stt.sherpa.OfflineModelConfig
import com.meetmind.assistant.stt.sherpa.OfflineNemoEncDecCtcModelConfig
import com.meetmind.assistant.stt.sherpa.OfflineRecognizer
import com.meetmind.assistant.stt.sherpa.OfflineRecognizerConfig
import com.meetmind.assistant.stt.sherpa.SileroVadModelConfig
import com.meetmind.assistant.stt.sherpa.Vad
import com.meetmind.assistant.stt.sherpa.VadModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sherpa-ONNX implementation of [SttRepository].
 *
 * Handles audio recording, VAD (Voice Activity Detection), and ASR (Automatic Speech Recognition)
 * using the Nemo Parakeet TDT 0.6B v3 Int8 model.
 *
 * Key characteristics:
 * - 16 kHz mono PCM capture via [AudioRecord]
 * - Silero VAD gates decode calls (512-sample windows, 6400-sample lookback)
 * - decodeMutex serialises all OfflineRecognizer.decode() calls (ONNX InferenceSession is NOT
 *   thread-safe; concurrent calls cause SIGSEGV in libonnxruntime)
 * - PerformanceHintManager hints the scheduler to prefer efficiency cores for the ASR thread
 *
 * spec 009 — T039
 */
@Singleton
class SherpaOnnxDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : SttRepository {

    companion object {
        private const val TAG = "SherpaOnnxDataSource"
        private const val SAMPLE_RATE_HZ = 16000
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val VAD_WINDOW_SIZE = 512
        private const val SPEECH_START_LOOKBACK_SAMPLES = 6400     // 0.4s
        private const val MIN_INFERENCE_INTERVAL_MS = 200L
        // 24000 samples = 1.5s at 16 kHz — minimum new audio before triggering partial inference
        private const val MIN_NEW_AUDIO_SAMPLES = 24000
        // 480000 samples = 30s at 16 kHz — cap to bound O(n²) inference growth
        private const val MAX_INFERENCE_AUDIO_SAMPLES = 480000
        // 48000 samples = 3s at 16 kHz — context carry-over across VAD segment boundaries
        private const val CONTEXT_CARRY_OVER_SAMPLES = 48000
        private const val INITIAL_BUFFER_CAPACITY = 160000 // ~10s pre-allocated
        private const val LOG_INTERVAL_READS = 50
    }

    // Managed instances — null when model is not loaded
    @Volatile private var activeRecognizer: OfflineRecognizer? = null
    @Volatile private var activeVad: Vad? = null

    // Serializes all recognizer.decode() calls. OfflineRecognizer is NOT thread-safe for
    // concurrent decode() invocations: the underlying ONNX InferenceSession has shared
    // internal state that causes SIGSEGV (fault addr 0x0/0x8 in libonnxruntime decode+128)
    // when two decode() calls run in parallel.
    private val decodeMutex = Mutex()

    @Volatile private var isRecording = false
    private var audioRecord: AudioRecord? = null

    // ── Pre-allocated growable float buffer ──────────────────────────────────────

    private class SampleBuffer(initialCapacity: Int = INITIAL_BUFFER_CAPACITY) {
        private var data = FloatArray(initialCapacity)
        var size: Int = 0
            private set

        fun append(samples: FloatArray) {
            ensureCapacity(size + samples.size)
            samples.copyInto(data, size)
            size += samples.size
        }

        fun getRange(from: Int, to: Int): FloatArray = data.copyOfRange(from, to)

        fun keepTail(samplesToKeep: Int) {
            val toKeep = samplesToKeep.coerceAtMost(size)
            if (toKeep <= 0) { size = 0; return }
            data.copyInto(data, 0, size - toKeep, size)
            size = toKeep
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity > data.size) {
                data = data.copyOf(maxOf(minCapacity, data.size * 2))
            }
        }
    }

    // ── Model lifecycle ──────────────────────────────────────────────────────────

    override suspend fun initialize(sttModelPath: String): Unit = withContext(Dispatchers.IO) {
        val modelDir = File(sttModelPath)
        Log.i(TAG, "Initializing STT from $modelDir")

        // Build OfflineRecognizer for Parakeet TDT via OfflineNemoEncDecCtcModelConfig
        // (Nemo CTC-like config works for TDT models in sherpa-onnx)
        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE_HZ, featureDim = 80),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(
                    model = File(modelDir, "encoder.int8.onnx").absolutePath
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "nemo_transducer"
            )
        )

        val recognizer = OfflineRecognizer(config = recognizerConfig)
        activeRecognizer = recognizer

        // Build Silero VAD — model loaded from assets ("silero_vad.onnx")
        val vadConfig = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.5f,
                minSpeechDuration = 0.25f,
                windowSize = VAD_WINDOW_SIZE,
                maxSpeechDuration = 30.0f
            ),
            sampleRate = SAMPLE_RATE_HZ,
            debug = false,
            numThreads = 1
        )
        val vad = Vad(config = vadConfig)
        // Drain + reset any leftover state
        while (!vad.empty()) vad.pop()
        vad.reset()
        activeVad = vad

        Log.i(TAG, "STT initialized (recognizer + VAD ready)")
    }

    @SuppressLint("MissingPermission")
    override fun startRecording(): Flow<RecognitionResult> = channelFlow {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress — forcing cleanup")
            stopRecording()
        }
        isRecording = true

        // Ensure VAD is reset for the new session
        withContext(Dispatchers.IO) {
            activeVad?.let { v ->
                while (!v.empty()) v.pop()
                v.reset()
            }
        }

        val samplesChannel = Channel<FloatArray>(Channel.UNLIMITED)

        // ── Audio recording coroutine ────────────────────────────────────────────
        val recordingJob = launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val numBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val actualBufferSize = numBytes * BUFFER_SIZE_MULTIPLIER

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )
            audioRecord?.startRecording()

            val readSize = (0.1 * SAMPLE_RATE_HZ).toInt() // 100ms chunks
            val buffer = ShortArray(readSize)
            var readCount = 0

            while (isRecording) {
                val ret = audioRecord?.read(buffer, 0, buffer.size)
                if (ret != null && ret > 0) {
                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                    if (samples.all { it.isFinite() }) {
                        samplesChannel.send(samples)
                    }
                }
                readCount++
                if (readCount % LOG_INTERVAL_READS == 0) {
                    Log.d(TAG, "Audio read #$readCount")
                }
            }
            samplesChannel.close()
            Log.i(TAG, "Recording job completed")
        }

        // ── VAD + ASR processing coroutine ───────────────────────────────────────
        withContext(Dispatchers.Default) {
            val buffer = SampleBuffer()
            var offset = 0
            var isSpeechStarted = false
            var startTime = System.currentTimeMillis()
            var speechStartOffset = 0
            var lastInferenceOffset = 0
            val isPartialInferenceBusy = AtomicBoolean(false)

            for (samples in samplesChannel) {
                buffer.append(samples)
                val vad = activeVad ?: continue

                // VAD processing with fixed window size
                while (offset + VAD_WINDOW_SIZE <= buffer.size) {
                    val vadWindow = buffer.getRange(offset, offset + VAD_WINDOW_SIZE)
                    if (vadWindow.all { it.isFinite() }) {
                        try {
                            vad.acceptWaveform(vadWindow)
                        } catch (e: Exception) {
                            Log.e(TAG, "VAD acceptWaveform failed", e)
                        }
                    }
                    offset += VAD_WINDOW_SIZE

                    if (!isSpeechStarted && vad.isSpeechDetected()) {
                        isSpeechStarted = true
                        speechStartOffset = maxOf(0, offset - SPEECH_START_LOOKBACK_SAMPLES)
                        startTime = System.currentTimeMillis()
                        lastInferenceOffset = speechStartOffset
                        Log.d(TAG, "Speech detected at offset $offset")
                    }
                }

                // Force segment split on long monologue (> 30s of continuous speech)
                val speechElapsedSamples = offset - speechStartOffset
                if (isSpeechStarted && speechElapsedSamples >= MAX_INFERENCE_AUDIO_SAMPLES) {
                    val cappedStart = maxOf(speechStartOffset, offset - MAX_INFERENCE_AUDIO_SAMPLES)
                    val audioSegment = buffer.getRange(cappedStart, offset)
                    try {
                        if (audioSegment.isNotEmpty() && audioSegment.all { it.isFinite() }) {
                            val trimmedText = decodeMutex.withLock {
                                val r = activeRecognizer ?: return@withLock null
                                val stream = r.createStream()
                                stream.acceptWaveform(audioSegment, SAMPLE_RATE_HZ)
                                r.decode(stream)
                                val result = r.getResult(stream)
                                stream.release()
                                result.text.trim().takeIf { it.isNotBlank() }
                            }
                            if (trimmedText != null) {
                                send(RecognitionResult(trimmedText, isComplete = true))
                                Log.d(TAG, "Forced split (${speechElapsedSamples / SAMPLE_RATE_HZ}s): ${trimmedText.take(50)}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Forced split inference failed", e)
                    }
                    isSpeechStarted = false
                    vad.reset()
                    buffer.keepTail(CONTEXT_CARRY_OVER_SAMPLES)
                    offset = buffer.size
                    speechStartOffset = 0
                    lastInferenceOffset = offset
                    startTime = System.currentTimeMillis()
                }

                // Partial inference on accumulated audio
                val elapsed = System.currentTimeMillis() - startTime
                val newAudioSamples = offset - lastInferenceOffset
                if (isSpeechStarted && elapsed > MIN_INFERENCE_INTERVAL_MS
                    && newAudioSamples >= MIN_NEW_AUDIO_SAMPLES
                    && isPartialInferenceBusy.compareAndSet(false, true)
                ) {
                    val cappedStart = maxOf(speechStartOffset, offset - MAX_INFERENCE_AUDIO_SAMPLES)
                    val audioSnapshot = buffer.getRange(cappedStart, offset)
                    lastInferenceOffset = offset
                    startTime = System.currentTimeMillis()

                    launch(Dispatchers.Default) {
                        try {
                            if (audioSnapshot.isNotEmpty() && audioSnapshot.all { it.isFinite() }) {
                                val trimmedText = decodeMutex.withLock {
                                    val r = activeRecognizer ?: return@withLock null
                                    val stream = r.createStream()
                                    stream.acceptWaveform(audioSnapshot, SAMPLE_RATE_HZ)
                                    r.decode(stream)
                                    val result = r.getResult(stream)
                                    stream.release()
                                    result.text.trim().takeIf { it.isNotBlank() }
                                }
                                if (trimmedText != null) {
                                    send(RecognitionResult(trimmedText, isComplete = false))
                                    Log.d(TAG, "Partial (${newAudioSamples}s new): ${trimmedText.take(50)}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Partial inference failed", e)
                        } finally {
                            isPartialInferenceBusy.set(false)
                        }
                    }
                }

                // Process completed VAD segments (end-of-speech detection)
                while (!vad.empty()) {
                    try {
                        val vadSegment = vad.front()
                        if (vadSegment.samples.isNotEmpty() && vadSegment.samples.all { it.isFinite() }) {
                            val trimmedText = decodeMutex.withLock {
                                val r = activeRecognizer ?: return@withLock null
                                val stream = r.createStream()
                                stream.acceptWaveform(vadSegment.samples, SAMPLE_RATE_HZ)
                                r.decode(stream)
                                val result = r.getResult(stream)
                                stream.release()
                                result.text.trim().takeIf { it.isNotBlank() }
                            }
                            if (trimmedText != null) {
                                send(RecognitionResult(trimmedText, isComplete = true))
                                Log.d(TAG, "decode() complete: ${trimmedText.take(80)}")
                            }
                        }
                        vad.pop()
                    } catch (e: Exception) {
                        Log.e(TAG, "VAD segment processing failed", e)
                        try { vad.pop() } catch (_: Exception) { break }
                    }
                    isSpeechStarted = false
                    buffer.keepTail(CONTEXT_CARRY_OVER_SAMPLES)
                    offset = buffer.size
                    lastInferenceOffset = offset
                }
            }

            // Flush: speech was active when stopRecording() was called
            if (isSpeechStarted && offset > speechStartOffset) {
                val cappedStart = maxOf(speechStartOffset, offset - MAX_INFERENCE_AUDIO_SAMPLES)
                val audioSegment = buffer.getRange(cappedStart, offset)
                if (audioSegment.isNotEmpty() && audioSegment.all { it.isFinite() }) {
                    try {
                        val trimmedText = decodeMutex.withLock {
                            val r = activeRecognizer ?: return@withLock null
                            val stream = r.createStream()
                            stream.acceptWaveform(audioSegment, SAMPLE_RATE_HZ)
                            r.decode(stream)
                            val result = r.getResult(stream)
                            stream.release()
                            result.text.trim().takeIf { it.isNotBlank() }
                        }
                        if (trimmedText != null) {
                            send(RecognitionResult(trimmedText, isComplete = true))
                            Log.d(TAG, "Flush on stop: ${trimmedText.take(50)}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Flush segment inference failed", e)
                    }
                }
            }

            Log.i(TAG, "Processing coroutine complete")
        }

        recordingJob.cancel()
    }

    override suspend fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Recording stopped")
    }

    override suspend fun releaseModel() {
        decodeMutex.withLock {
            activeRecognizer?.release()
            activeRecognizer = null
        }
        Log.i(TAG, "STT model released from native heap")
    }
}
