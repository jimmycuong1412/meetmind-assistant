package com.meetmind.assistant.domain.model

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
 * @property skeleton Keyword bullets the candidate speaks **from**, rather than reads.
 *   3-5 short fragments ("S3 backend + DynamoDB lock table"), glanceable in ~2 s.
 *
 *   This is the primary output of Interview Mode. Reading generated prose aloud in a
 *   live interview is audible to the interviewer — cadence flattens, eye-line shifts —
 *   and produces a worse answer than the candidate's own words. Structure prompts
 *   recall; sentences invite recitation.
 *
 *   Empty for insights persisted before this field existed; [answerSuggestion] then
 *   carries the prose and the parser synthesises a single-item skeleton from it.
 * @property isSkeletonSynthesised True when [skeleton] was derived from prose rather
 *   than returned by the model. Such a skeleton is shown but never persisted into the
 *   tasks column: it would duplicate text already in the answer, and would make a prose
 *   insight indistinguishable from a genuine one-bullet skeleton.
 * @property questionType Shape hint for the answer, or null when the model omitted it
 *   or returned an unrecognised value. Null means "no hint" — never assume a default.
 * @property depthProbe The follow-up the candidate should expect, e.g. "expect a
 *   follow-up on orphaned locks". Senior interviews are decided on the second and third
 *   follow-up rather than the first answer, so cueing what is coming is worth as much as
 *   the answer itself. Null when the model did not supply one.
 */
data class InterviewInsight(
    val questionDetected: Boolean,
    val detectedQuestion: String?,
    val answerSuggestion: String,
    val coachingTips: List<String>,
    val role: String,
    val skeleton: List<String> = emptyList(),
    val isSkeletonSynthesised: Boolean = false,
    val questionType: QuestionType? = null,
    val depthProbe: String? = null
)
