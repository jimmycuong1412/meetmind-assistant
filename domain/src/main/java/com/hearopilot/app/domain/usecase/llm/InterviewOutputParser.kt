package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.InterviewInsight
import com.hearopilot.app.domain.model.LlmInsight
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

            InterviewInsight(
                questionDetected = questionDetected,
                detectedQuestion = detectedQuestion,
                answerSuggestion = answer.trim(),
                coachingTips = coachingTips,
                role = role
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
            sourceSegmentIds = sourceSegmentIds
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
