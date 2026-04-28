package com.meetmind.assistant.domain.usecase.transcription

import com.meetmind.assistant.domain.model.SearchResult
import com.meetmind.assistant.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for full-text search across transcription sessions.
 *
 * Delegates to [TranscriptionRepository.searchTranscriptions] and returns a
 * [Flow] of ranked [SearchResult] items.
 */
class SearchTranscriptionsUseCase(
    private val repository: TranscriptionRepository
) {
    operator fun invoke(query: String): Flow<List<SearchResult>> =
        repository.searchTranscriptions(query)
}
