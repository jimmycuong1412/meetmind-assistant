package com.hearopilot.app.domain.model

/**
 * Modes of operation for the transcription session.
 * Each mode determines:
 * - AI behavior (Summary only vs Tasks/Suggestions)
 * - System Prompt used
 * - Insight generation frequency
 */
enum class RecordingMode {
    /**
     * Basic transcription with a final summary.
     * AI: Summary only.
     */
    SIMPLE_LISTENING,

    /**
     * For brief meetings.
     * AI: Summary + Suggestions + Tasks.
     * Frequency: High.
     */
    SHORT_MEETING,

    /**
     * For extended conferences involved many speakers/topics.
     * AI: Summary + Suggestions + Tasks.
     * Frequency: Low (interval-based).
     */
    LONG_MEETING,

    /**
     * Real-time translation of the input audio.
     * AI: Translates text segments.
     */
    REAL_TIME_TRANSLATION,

    /**
     * Mock job-interview coaching.
     * AI: Detects questions in the conversation and delivers timely, role-aware answers
     * with suggested talking points and follow-up advice.
     * Role is conveyed via the topic field (e.g. "Developer", "Product Manager").
     */
    INTERVIEW
}
