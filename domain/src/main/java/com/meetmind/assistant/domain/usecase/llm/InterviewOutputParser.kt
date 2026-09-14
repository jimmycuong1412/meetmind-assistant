package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.model.InterviewInsight
import com.meetmind.assistant.domain.model.LlmInsight
import com.meetmind.assistant.domain.model.QuestionType
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
     * Marks the depth-probe line appended to [LlmInsight.content] during persistence.
     *
     * Interview insights reuse the `llm_insights` table rather than adding columns, so
     * the probe rides along in `content` behind this marker. The UI splits on it to
     * render the probe as a distinct cue; readers that don't know the marker still show
     * a sensible (if slightly longer) answer, which keeps old and new clients compatible.
     */
    const val DEPTH_PROBE_MARKER = "→ "

    /**
     * Divides skeleton bullets from coaching tips inside the shared `tasks` JSON array.
     *
     * Written only when both sections are present, so an insight carrying tips alone
     * serialises byte-identically to how it did before skeletons existed — which keeps
     * already-persisted rows readable and their contracts intact.
     */
    const val TIPS_SEPARATOR = "—"

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

            val rawAnswer = InsightOutputParser.extractJsonField(cleaned, "answer")
                ?: InsightOutputParser.extractJsonField(cleaned, "summary") // backward compat

            val coachingTips = extractJsonArray(cleaned, "coaching_tips")
                .ifEmpty { extractJsonArray(cleaned, "action_items") } // backward compat

            // Skeleton is the primary output; prose is the legacy path. When the model
            // returns only prose (old schema, or a model that ignored the instruction),
            // synthesise a single-item skeleton so the UI always has bullets to render.
            //
            // The synthesised form is marked by [isSkeletonSynthesised] so persistence can
            // skip it: writing it into the tasks column would duplicate text that already
            // lives in `content`, and would make a prose insight indistinguishable from one
            // where the model genuinely returned a one-bullet skeleton.
            val parsedSkeleton = extractJsonArray(cleaned, "skeleton")
            val skeletonSynthesised = parsedSkeleton.isEmpty()
            val skeleton = parsedSkeleton.ifEmpty {
                rawAnswer?.trim()?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }

            // answerSuggestion still backs LlmInsight.content, so it must never be blank
            // when the model returned a skeleton but no prose.
            val answer = rawAnswer
                ?: skeleton.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                ?: cleaned // last resort: use raw output

            InterviewInsight(
                questionDetected = questionDetected,
                detectedQuestion = detectedQuestion,
                answerSuggestion = answer.trim(),
                coachingTips = coachingTips,
                role = role,
                skeleton = skeleton,
                isSkeletonSynthesised = skeletonSynthesised,
                questionType = QuestionType.fromWireValue(
                    InsightOutputParser.extractJsonField(cleaned, "question_type")
                ),
                depthProbe = InsightOutputParser.extractJsonField(cleaned, "depth_probe")
                    ?.takeIf { it.isNotBlank() }
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

        // The tasks column carries the skeleton bullets, then the coaching tips, with a
        // separator between them. Reusing this JSON-array column keeps Interview Mode
        // free of any schema migration (the trade the prose schema already made), and
        // the separator lets the UI style skeleton bullets — the thing the candidate
        // actually speaks from — differently from delivery advice.
        //
        // The separator is emitted only when BOTH sections are non-empty, so an insight
        // with tips alone still serialises exactly as it did before this field existed.
        // That keeps previously-persisted rows and their tests valid.
        // A synthesised skeleton is just the prose answer restated; persisting it would
        // duplicate  and blur the line between "model gave one bullet" and
        // "model gave prose". Only genuine model-authored skeletons are written.
        val persistedSkeleton =
            if (interviewInsight.isSkeletonSynthesised) emptyList() else interviewInsight.skeleton
        val hasSkeleton = persistedSkeleton.isNotEmpty()
        val hasTips = interviewInsight.coachingTips.isNotEmpty()
        val tasksItems = when {
            hasSkeleton && hasTips ->
                persistedSkeleton + TIPS_SEPARATOR + interviewInsight.coachingTips
            hasSkeleton -> persistedSkeleton
            else -> interviewInsight.coachingTips
        }
        val tasksJson = if (tasksItems.isNotEmpty()) {
            val escaped = tasksItems.joinToString(",") { item ->
                "\"${item.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
            "[$escaped]"
        } else null

        // Append the depth probe to the content so it survives persistence without a new
        // column. Prefixed with a stable marker so the UI can split it back out and style
        // it differently from the answer itself.
        val content = interviewInsight.depthProbe
            ?.takeIf { it.isNotBlank() }
            ?.let { "${interviewInsight.answerSuggestion}\n$DEPTH_PROBE_MARKER$it" }
            ?: interviewInsight.answerSuggestion

        return LlmInsight(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            title = title,
            content = content,
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
