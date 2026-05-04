package com.meetmind.assistant.domain.model

/**
 * Running filler-word statistics for an Interview Mode recording session.
 *
 * Computed incrementally from completed transcription segments — never persisted.
 * Reset to defaults when a new session starts.
 *
 * @property totalCount Total number of filler words detected so far.
 * @property ratePerMinute Fillers per minute: totalCount / max(1f, durationMin).
 *   Applied from the first segment onward with no minimum-duration gate; early
 *   values may be inflated when the session is only a few seconds old.
 */
data class FillerWordStats(
    val totalCount: Int = 0,
    val ratePerMinute: Float = 0f
)
