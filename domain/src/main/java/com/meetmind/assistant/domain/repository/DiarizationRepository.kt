package com.meetmind.assistant.domain.repository

import com.meetmind.assistant.domain.model.DiarizationResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository for offline (end-of-session) speaker diarization.
 *
 * Backed by sherpa-onnx's `OfflineSpeakerDiarization` (Pyannote segmentation
 * + 3D-Speaker embedding + agglomerative clustering). The model files live
 * on disk and are downloaded on demand by [isModelAvailable] / the model
 * download flow.
 *
 * Thread model: implementations may pin the actual native run to a single
 * background dispatcher; callers are free to invoke from any context.
 */
interface DiarizationRepository {
    /**
     * True when the diarization model bundle is fully present on disk and
     * ready to use. False indicates the user hasn't downloaded it yet (or a
     * partial download is sitting in the model directory).
     */
    fun isModelAvailable(): Boolean

    /**
     * Run diarization on the WAV file at [audioFilePath].
     *
     * The returned [Flow] emits a `Float` progress fraction in [0f, 1f] (or
     * negative values to signal indeterminate progress) and completes with a
     * single [DiarizationResult]-bearing terminal value via the provided
     * sink. Exceptions surface through `Flow.catch` on the caller.
     *
     * Implementations must respect coroutine cancellation: if the caller
     * cancels the collecting job, native processing should stop at the next
     * segmentation chunk boundary.
     */
    suspend fun diarize(audioFilePath: String): Result<DiarizationResult>
}
