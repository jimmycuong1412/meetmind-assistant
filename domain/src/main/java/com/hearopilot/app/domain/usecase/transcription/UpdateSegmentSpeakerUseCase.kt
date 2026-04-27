package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for assigning or clearing a manual speaker label on a transcription segment.
 *
 * @param segmentId The segment to update
 * @param speaker Display label (e.g. "Me", "Person A"), or null to clear
 */
class UpdateSegmentSpeakerUseCase(
    private val repository: TranscriptionRepository
) {
    suspend operator fun invoke(segmentId: String, speaker: String?): Result<Unit> =
        repository.updateSegmentSpeaker(segmentId, speaker)
}
