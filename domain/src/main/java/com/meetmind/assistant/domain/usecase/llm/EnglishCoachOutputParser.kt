package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.model.EnglishCoachInsight
import com.meetmind.assistant.domain.model.LlmInsight
import java.util.UUID

object EnglishCoachOutputParser {

    /**
     * Parse the raw LLM output into an [EnglishCoachInsight].
     *
     * Expected JSON schema:
     * ```json
     * {
     *   "original": "I goes to the office yesterday",
     *   "corrected": "I went to the office yesterday",
     *   "is_correct": false,
     *   "polish": "I headed to the office yesterday.",
     *   "coaching_tip": "Use past tense 'went' not present 'goes' for yesterday."
     * }
     * ```
     *
     * Falls back to wrapping the raw output as a coaching tip if JSON is malformed.
     */
    fun parse(rawOutput: String, context: String): EnglishCoachInsight {
        val cleaned = InsightOutputParser.stripCodeFences(
            InsightOutputParser.stripThinkingBlock(rawOutput)
        )

        return try {
            val original = InsightOutputParser.extractJsonField(cleaned, "original")
                ?: cleaned.trim().take(200)
            val corrected = InsightOutputParser.extractJsonField(cleaned, "corrected")
                ?: original
            val isCorrect = extractIsCorrect(cleaned)
            val polish = InsightOutputParser.extractJsonField(cleaned, "polish")
                ?.takeIf { it.isNotBlank() && it.lowercase() != "null" }
            val coachingTip = InsightOutputParser.extractJsonField(cleaned, "coaching_tip")
                ?.takeIf { it.isNotBlank() && it.lowercase() != "null" }

            EnglishCoachInsight(
                original = original.trim(),
                corrected = corrected.trim(),
                isCorrect = isCorrect,
                polish = polish?.trim(),
                coachingTip = coachingTip?.trim(),
                context = context
            )
        } catch (e: Exception) {
            EnglishCoachInsight(
                original = cleaned.trim().take(200),
                corrected = cleaned.trim().take(200),
                isCorrect = true,
                polish = null,
                coachingTip = cleaned.trim().takeIf { it.isNotBlank() },
                context = context
            )
        }
    }

    /**
     * Map an [EnglishCoachInsight] to [LlmInsight] for persistence.
     *
     * Encoding:
     * - `title`        ← original phrase (truncated to 80 chars)
     * - `content`      ← corrected phrase
     * - `tasks`        ← JSON array of non-null [polish, coaching_tip]
     * - `questionType` ← context ("daily" | "professional") — reuses the column
     */
    fun toLlmInsight(
        insight: EnglishCoachInsight,
        sessionId: String,
        timestamp: Long,
        sourceSegmentIds: List<String>
    ): LlmInsight {
        val tips = listOfNotNull(insight.polish, insight.coachingTip)
        val tasksJson = if (tips.isNotEmpty()) {
            val escaped = tips.joinToString(",") { tip ->
                "\"${tip.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
            "[$escaped]"
        } else null

        return LlmInsight(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            title = insight.original.take(80),
            content = insight.corrected,
            tasks = tasksJson,
            timestamp = timestamp,
            sourceSegmentIds = sourceSegmentIds,
            questionType = insight.context
        )
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun extractIsCorrect(json: String): Boolean {
        val pattern = Regex(""""is_correct"\s*:\s*(true|false|"true"|"false")""", RegexOption.IGNORE_CASE)
        val match = pattern.find(json) ?: return true  // default true = no errors
        val raw = match.groupValues[1].trim().lowercase().removeSurrounding("\"")
        return raw == "true"
    }
}
