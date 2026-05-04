package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.model.InterviewInsight
import com.meetmind.assistant.domain.model.LlmInsight
import java.util.UUID

/**
 * Parser for the dual-output JSON schema produced by the Interview Mode LLM prompt.
 *
 * ## Expected JSON schema
 * ```json
 * {
 *   "question_detected": true,
 *   "detected_question": "Can you walk me through a challenging project?",
 *   "answer": "I led a migration of our monolith to microservices…",
 *   "coaching_tips": [
 *     "Open with a concrete metric to anchor the story",
 *     "Use the STAR framework: Situation → Task → Action → Result"
 *   ]
 * }
 * ```
 *
 * When no question is detected the model omits or sets `question_detected` to false
 * and `detected_question` to null, while still providing an `answer` (coaching note)
 * and `coaching_tips`.
 *
 * ## Robustness guarantees
 * - Code fences and `<think>` blocks are stripped before parsing (delegated to
 *   [InsightOutputParser]).
 * - All fields fall back gracefully: missing `question_detected` → false, missing
 *   `answer` → raw output, missing `coaching_tips` → empty list.
 * - The `question_detected` field accepts `true`/`"true"` as truthy values so the
 *   parser handles both boolean and string representations from small models.
 *
 * ## Mapping to [LlmInsight] for persistence
 * Interview insights are stored in the existing `llm_insights` table without any
 * schema migration:
 *   - `title`   ← detected question text (or generic label when no question found)
 *   - `content` ← answer suggestion
 *   - `tasks`   ← coaching tips as JSON array
 *
 * The [InterviewInsight] model is the in-memory typed representation; [LlmInsight]
 * is the persistence form.
 */
object InterviewOutputParser {

    // Sentinel prefix written into the title when the model found no question.
    // Used by the UI to distinguish coaching notes from answer suggestions.
    const val COACHING_NOTE_PREFIX = "coaching:"

    // Behavioural question opener phrases (R-06 from research.md).
    // Used by the client-side pre-filter when the LLM classifies a known behavioural
    // question as something other than "behavioural" (e.g. small model misses it).
    private val BEHAVIOURAL_OPENERS = listOf(
        "tell me about a time",
        "give me an example",
        "give an example",
        "describe a situation",
        "describe a time",
        "walk me through a time",
        "have you ever",
        "tell me about when",
        "can you describe a time"
    )

    /**
     * Parse the raw LLM output into an [InterviewInsight].
     *
     * @param rawOutput Token-collected string from the LLM (may include code fences
     *   or `<think>` blocks).
     * @param role The candidate's target role (e.g. "Developer"), injected for context.
     * @return Parsed [InterviewInsight], or a fallback insight if parsing fails entirely.
     */
    fun parse(rawOutput: String, role: String): InterviewInsight {
        // Reuse existing cleaning utilities from InsightOutputParser
        val cleaned = InsightOutputParser.stripCodeFences(
            InsightOutputParser.stripThinkingBlock(rawOutput)
        )

        return try {
            val questionDetected = extractBooleanField(cleaned, "question_detected")
            val detectedQuestion = if (questionDetected) {
                InsightOutputParser.extractJsonField(cleaned, "detected_question")
                    ?.takeIf { it.isNotBlank() }
            } else null

            val answer = InsightOutputParser.extractJsonField(cleaned, "answer")
                ?: InsightOutputParser.extractJsonField(cleaned, "summary") // backward compat
                ?: cleaned // last resort: use raw output as answer

            val coachingTips = extractJsonArray(cleaned, "coaching_tips")
                .ifEmpty { extractJsonArray(cleaned, "action_items") } // backward compat

            // Extract question_type; coerce any value not in the allowed set to null.
            val questionType = if (questionDetected) {
                val raw = InsightOutputParser.extractJsonField(cleaned, "question_type")
                    ?.lowercase()?.trim()
                when (raw) {
                    "behavioural", "behavioral" -> "behavioural" // accept US spelling too
                    "technical"                 -> "technical"
                    "situational"               -> "situational"
                    else                        -> null
                }
            } else null

            // Client-side behavioural keyword pre-filter (T033 / R-06):
            // If the LLM failed to classify a known behavioural opener, upgrade it here.
            // Only applied when the detected question is non-null and the LLM did not
            // already return "behavioural" — prevents downgrading a correct classification.
            val resolvedQuestionType = if (questionType == null && detectedQuestion != null) {
                val qLower = detectedQuestion.lowercase()
                if (BEHAVIOURAL_OPENERS.any { qLower.contains(it) }) "behavioural" else null
            } else questionType

            InterviewInsight(
                questionDetected = questionDetected,
                detectedQuestion = detectedQuestion,
                answerSuggestion = answer.trim(),
                coachingTips = coachingTips,
                role = role,
                questionType = resolvedQuestionType
            )
        } catch (e: Exception) {
            // Fully degraded fallback: treat raw output as an answer with no tips
            InterviewInsight(
                questionDetected = false,
                detectedQuestion = null,
                answerSuggestion = cleaned.trim().ifBlank { rawOutput.trim() },
                coachingTips = emptyList(),
                role = role
            )
        }
    }

    /**
     * Map an [InterviewInsight] to an [LlmInsight] for persistence in the existing
     * `llm_insights` table. No database migration required.
     *
     * Title encoding:
     *   - Question detected → the detected question text (truncated to 120 chars)
     *   - No question       → `"coaching:<role>"` so the UI can render a coaching-note
     *     badge instead of a question chip
     */
    fun toLlmInsight(
        interviewInsight: InterviewInsight,
        sessionId: String,
        timestamp: Long,
        sourceSegmentIds: List<String>
    ): LlmInsight {
        val title = if (interviewInsight.questionDetected && interviewInsight.detectedQuestion != null) {
            interviewInsight.detectedQuestion.take(120)
        } else {
            "$COACHING_NOTE_PREFIX${interviewInsight.role}"
        }

        val tasksJson = if (interviewInsight.coachingTips.isNotEmpty()) {
            val escaped = interviewInsight.coachingTips.joinToString(",") { tip ->
                "\"${tip.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
            "[$escaped]"
        } else null

        return LlmInsight(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            title = title,
            content = interviewInsight.answerSuggestion,
            tasks = tasksJson,
            timestamp = timestamp,
            sourceSegmentIds = sourceSegmentIds,
            questionType = interviewInsight.questionType
        )
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Extract a boolean field from a JSON-like string.
     * Accepts `true`, `"true"` (case-insensitive), and `1` as truthy.
     */
    private fun extractBooleanField(json: String, field: String): Boolean {
        // Try to find "field": true  or  "field": false
        val boolPattern = Regex(""""$field"\s*:\s*(true|false|"true"|"false")""", RegexOption.IGNORE_CASE)
        val match = boolPattern.find(json)
        if (match != null) {
            val raw = match.groupValues[1].trim().lowercase().removeSurrounding("\"")
            return raw == "true"
        }
        // Numeric fallback: "field": 1
        val numPattern = Regex(""""$field"\s*:\s*(\d+)""")
        return numPattern.find(json)?.groupValues?.get(1)?.trim() == "1"
    }

    /**
     * Extract a JSON array of strings from a JSON-like string.
     * Delegates to [InsightOutputParser.extractJsonArray].
     */
    private fun extractJsonArray(json: String, field: String): List<String> =
        InsightOutputParser.extractJsonArray(json, field)
}
