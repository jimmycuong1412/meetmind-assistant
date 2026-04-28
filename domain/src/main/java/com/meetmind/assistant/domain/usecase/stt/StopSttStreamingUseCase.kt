package com.meetmind.assistant.domain.usecase.stt

import com.meetmind.assistant.domain.repository.SttRepository

/**
 * Use case to stop STT streaming and release resources.
 *
 * @property sttRepository Repository for STT operations
 */
class StopSttStreamingUseCase(
    private val sttRepository: SttRepository
) {
    /**
     * Stop STT streaming.
     */
    suspend operator fun invoke() {
        sttRepository.stopStreaming()
    }
}
