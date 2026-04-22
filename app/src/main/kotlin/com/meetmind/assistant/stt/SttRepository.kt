// spec 009 — T038: SttRepository interface for the Sherpa-ONNX STT pipeline
package com.meetmind.assistant.stt

import com.meetmind.assistant.data.model.RecognitionResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstraction over the on-device speech recognition pipeline.
 *
 * Lifecycle:
 *  1. [initialize] — load the ONNX model from [sttModelPath] into native heap (~670 MB)
 *  2. [startRecording] — begin capturing mic audio; emits [RecognitionResult] fragments
 *  3. [stopRecording] — stop the AudioRecord; flushes the last VAD segment
 *  4. [releaseModel] — free the native ONNX model heap between sessions
 *
 * Implementations must serialise all `decode()` calls with a [kotlinx.coroutines.sync.Mutex]
 * to prevent SIGSEGV in the shared ONNX InferenceSession.
 *
 * spec 009 — T038
 */
interface SttRepository {

    /**
     * Load the ONNX ASR model from [sttModelPath] (directory containing Parakeet TDT files).
     * Suspends on [kotlinx.coroutines.Dispatchers.IO]; idempotent if already loaded.
     */
    suspend fun initialize(sttModelPath: String)

    /**
     * Start capturing microphone audio and emitting recognised speech.
     *
     * Emits:
     *  - [RecognitionResult] with `isComplete = false` for partial/interim results
     *  - [RecognitionResult] with `isComplete = true` for final VAD segments
     *
     * The Flow completes when [stopRecording] is called.
     */
    fun startRecording(): Flow<RecognitionResult>

    /**
     * Stop the AudioRecord and signal the recording loop to flush any pending audio.
     * Safe to call even if [startRecording] was never called (idempotent).
     */
    suspend fun stopRecording()

    /**
     * Release the native ONNX model and free native heap.
     * Call between sessions to reclaim ~670 MB. Guarded by decodeMutex to prevent
     * use-after-free in concurrent decode calls.
     */
    suspend fun releaseModel()
}
