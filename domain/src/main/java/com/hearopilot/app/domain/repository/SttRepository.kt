package com.hearopilot.app.domain.repository

import com.hearopilot.app.domain.model.TranscriptionSegment
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Speech-To-Text operations.
 *
 * Provides streaming transcription from audio input using Sherpa-ONNX models.
 */
interface SttRepository {
    /**
     * Initialize the STT engine with a model and language.
     *
     * @param modelPath Absolute path to the ONNX model directory
     * @param languageCode BCP-47 language code (e.g. "en", "vi")
     * @return Result indicating success or failure
     */
    suspend fun initialize(modelPath: String, languageCode: String): Result<Unit>

    /**
     * Start streaming audio recording and transcription.
     *
     * @return Flow of transcription segments. Flow never completes until stopStreaming is called.
     */
    fun startStreaming(): Flow<TranscriptionSegment>

    /**
     * Stop streaming and release audio resources.
     */
    suspend fun stopStreaming()

    /**
     * Release the native STT model from memory (~670 MB footprint).
     *
     * Safe to call after stopStreaming() completes. The model will be lazily
     * recreated on the next initialize() call.
     */
    suspend fun releaseModel()
}
