package com.hearopilot.app.domain.model

/**
 * Typed dual-output result for a single Interview Mode inference pass.
 *
 * A single LLM call in Interview Mode produces two semantically distinct outputs:
 *
 * 1. **Answer suggestion** — a direct, role-tailored response the candidate can use
 *    verbatim or as a jumping-off point when a question is detected.
 * 2. **Coaching tips** — procedural / behavioural guidance that is always present,
 *    regardless of whether a question was detected (e.g. "speak more slowly", "add
 *    a concrete example").
 *
 * When [questionDetected] is false, [answerSuggestion] contains a general coaching
 * observation rather than a question-specific answer. [detectedQuestion] is null.
 *
 * This model is **transient** — it is produced by [InterviewOutputParser] and
 * immediately mapped to an [LlmInsight] for persistence. The insight's fields carry:
 *   - `title`   = [detectedQuestion] if detected, else a generic coaching label
 *   - `content` = [answerSuggestion]
 *   - `tasks`   = [coachingTips] serialised as a JSON array
 *
 * @property questionDetected True when the LLM detected ≥1 interview question in the
 *   transcript chunk. False when no question was found and the output is a general
 *   coaching note.
 * @property detectedQuestion The verbatim question extracted from the transcript, or
 *   null when [questionDetected] is false.
 * @property answerSuggestion The suggested answer with 2-3 key talking points, always
 *   tailored to the candidate's target [role].
 * @property coachingTips Ordered list of follow-up coaching tips or behavioural
 *   recommendations. Always non-empty (minimum one tip).
 * @property role The target role used for prompt generation (e.g. "Developer").
 */
data class InterviewInsight(
    val questionDetected: Boolean,
    val detectedQuestion: String?,
    val answerSuggestion: String,
    val coachingTips: List<String>,
    val role: String
)
