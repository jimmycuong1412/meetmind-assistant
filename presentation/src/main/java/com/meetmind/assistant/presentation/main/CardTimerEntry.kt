package com.meetmind.assistant.presentation.main

/**
 * Per-card answer-duration timer state for Interview Mode coaching cards.
 *
 * Held in [MainUiState.cardTimers] keyed by [com.meetmind.assistant.domain.model.LlmInsight.id].
 * Incremented by a 1-second ticker coroutine in [MainViewModel] while recording is active.
 * Freezes when recording stops; [nudge] is set retrospectively when elapsed < 30 s.
 *
 * @property elapsedMs Milliseconds elapsed since the first segment after the question card.
 * @property nudge "Consider elaborating" when the answer was < 30 s; null otherwise.
 *   Set only when recording stops or a new question card fires — never shown live.
 */
data class CardTimerEntry(
    val elapsedMs: Long = 0L,
    val nudge: String? = null
)
