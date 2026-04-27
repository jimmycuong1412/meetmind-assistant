package com.hearopilot.app.domain.usecase.stt

import com.hearopilot.app.domain.repository.SttRepository

/**
 * Use case to initialize and start STT streaming.
 *
 * @property sttRepository Repository for STT operations
 */
class StartSttStreamingUseCase(
    private val sttRepository: SttRepository
) {
    /**
     * Initialize STT with the specified model and language.
     *
     * @param modelPath Absolute path to the ONNX model directory
     * @param languageCode BCP-47 language code (e.g. "en", "vi")
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(modelPath: String, languageCode: String = "en"): Result<Unit> {
        return sttRepository.initialize(modelPath, languageCode)
    }
}
