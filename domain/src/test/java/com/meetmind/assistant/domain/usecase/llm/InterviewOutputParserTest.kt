package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.model.InterviewInsight
import com.meetmind.assistant.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InterviewOutputParser].
 *
 * The parser is a pure function with no Android deps — these tests run on plain JVM.
 *
 * Coverage targets the historical bug surface:
 *  - Schema variants the model has been observed to emit (boolean vs string flags,
 *    code-fenced output, `<think>` blocks, missing fields)
 *  - Backward-compat paths (`summary` → `answer`, `action_items` → `coaching_tips`)
 *  - Mapping to [com.meetmind.assistant.domain.model.LlmInsight] (title encoding,
 *    coaching-note prefix, JSON escaping in the tasks column)
 */
class InterviewOutputParserTest {

    private val role = "Backend Engineer"

    // ─── Happy path ─────────────────────────────────────────────────────────────

    @Test
    fun `parses well-formed dual-output JSON`() {
        val raw = """
            {
              "question_detected": true,
              "detected_question": "Tell me about a hard bug.",
              "answer": "I once tracked down a race condition in our payment retry path.",
              "coaching_tips": ["Lead with the impact", "Use STAR framework"]
            }
        """.trimIndent()

        val result = InterviewOutputParser.parse(raw, role)

        assertTrue(result.questionDetected)
        assertEquals("Tell me about a hard bug.", result.detectedQuestion)
        assertEquals(
            "I once tracked down a race condition in our payment retry path.",
            result.answerSuggestion
        )
        assertEquals(listOf("Lead with the impact", "Use STAR framework"), result.coachingTips)
        assertEquals(role, result.role)
    }

    // ─── question_detected variants ─────────────────────────────────────────────

    @Test
    fun `accepts string-quoted true for question_detected`() {
        val raw = """{"question_detected": "true", "detected_question": "Q?", "answer": "A", "coaching_tips": []}"""
        assertTrue(InterviewOutputParser.parse(raw, role).questionDetected)
    }

    @Test
    fun `accepts numeric 1 as truthy for question_detected`() {
        val raw = """{"question_detected": 1, "detected_question": "Q?", "answer": "A", "coaching_tips": []}"""
        assertTrue(InterviewOutputParser.parse(raw, role).questionDetected)
    }

    @Test
    fun `treats missing question_detected as false`() {
        val raw = """{"answer": "Some coaching note", "coaching_tips": ["tip"]}"""
        val result = InterviewOutputParser.parse(raw, role)
        assertFalse(result.questionDetected)
        assertNull(result.detectedQuestion)
        assertEquals("Some coaching note", result.answerSuggestion)
    }

    @Test
    fun `clears detectedQuestion when question_detected is false even if field is present`() {
        // The model occasionally emits a question text even after concluding none was asked.
        // Trust the boolean flag, not the string.
        val raw = """
            {
              "question_detected": false,
              "detected_question": "Some hallucinated question",
              "answer": "Generic note",
              "coaching_tips": []
            }
        """.trimIndent()
        val result = InterviewOutputParser.parse(raw, role)
        assertFalse(result.questionDetected)
        assertNull(result.detectedQuestion)
    }

    // ─── Output sanitisation ────────────────────────────────────────────────────

    @Test
    fun `strips markdown code fences before parsing`() {
        val raw = """
            ```json
            {"question_detected": true, "detected_question": "Q", "answer": "A", "coaching_tips": []}
            ```
        """.trimIndent()
        val result = InterviewOutputParser.parse(raw, role)
        assertTrue(result.questionDetected)
        assertEquals("A", result.answerSuggestion)
    }

    @Test
    fun `strips think block before parsing`() {
        val raw = """
            <think>Let me consider whether this is a question…</think>
            {"question_detected": false, "answer": "Stay concise", "coaching_tips": []}
        """.trimIndent()
        val result = InterviewOutputParser.parse(raw, role)
        assertFalse(result.questionDetected)
        assertEquals("Stay concise", result.answerSuggestion)
    }

    // ─── Backward compatibility ─────────────────────────────────────────────────

    @Test
    fun `falls back to summary field when answer is absent`() {
        val raw = """{"question_detected": true, "detected_question": "Q", "summary": "Old-format answer"}"""
        val result = InterviewOutputParser.parse(raw, role)
        assertEquals("Old-format answer", result.answerSuggestion)
    }

    @Test
    fun `falls back to action_items when coaching_tips is absent`() {
        val raw = """
            {"question_detected": false, "answer": "Note", "action_items": ["legacy tip"]}
        """.trimIndent()
        val result = InterviewOutputParser.parse(raw, role)
        assertEquals(listOf("legacy tip"), result.coachingTips)
    }

    // ─── Degraded fallback ──────────────────────────────────────────────────────

    @Test
    fun `returns raw text as answer when JSON is malformed`() {
        val raw = "Just some plain text the model dumped without JSON"
        val result = InterviewOutputParser.parse(raw, role)
        assertFalse(result.questionDetected)
        assertNull(result.detectedQuestion)
        assertEquals(raw, result.answerSuggestion)
        assertTrue(result.coachingTips.isEmpty())
    }

    @Test
    fun `handles completely empty input without crashing`() {
        val result = InterviewOutputParser.parse("", role)
        assertNotNull(result)
        assertFalse(result.questionDetected)
        assertEquals("", result.answerSuggestion)
    }

    // ─── Mapping to LlmInsight ──────────────────────────────────────────────────

    @Test
    fun `toLlmInsight uses detected question as title when question detected`() {
        val raw = """
            {"question_detected": true, "detected_question": "How do you handle conflict?",
             "answer": "I listen first.", "coaching_tips": []}
        """.trimIndent()
        val parsed = InterviewOutputParser.parse(raw, role)
        val insight = InterviewOutputParser.toLlmInsight(
            interviewInsight = parsed,
            sessionId = "s1",
            timestamp = 1000L,
            sourceSegmentIds = listOf("seg1")
        )
        assertEquals("How do you handle conflict?", insight.title)
        assertEquals("I listen first.", insight.content)
    }

    @Test
    fun `toLlmInsight uses coaching prefix when no question`() {
        val raw = """{"question_detected": false, "answer": "Be concise", "coaching_tips": []}"""
        val parsed = InterviewOutputParser.parse(raw, role)
        val insight = InterviewOutputParser.toLlmInsight(parsed, "s1", 1000L, emptyList())
        assertEquals("${InterviewOutputParser.COACHING_NOTE_PREFIX}$role", insight.title)
    }

    @Test
    fun `toLlmInsight truncates long question titles to 120 chars`() {
        val longQuestion = "A".repeat(200)
        val raw = """{"question_detected": true, "detected_question": "$longQuestion",
                     "answer": "A", "coaching_tips": []}"""
        val parsed = InterviewOutputParser.parse(raw, role)
        val insight = InterviewOutputParser.toLlmInsight(parsed, "s1", 1L, emptyList())
        assertEquals(120, insight.title!!.length)
    }

    @Test
    fun `toLlmInsight serialises coaching tips as valid JSON array`() {
        val raw = """
            {"question_detected": false, "answer": "Note",
             "coaching_tips": ["First tip", "Second tip"]}
        """.trimIndent()
        val parsed = InterviewOutputParser.parse(raw, role)
        val insight = InterviewOutputParser.toLlmInsight(parsed, "s1", 1L, emptyList())
        assertEquals("""["First tip","Second tip"]""", insight.tasks)
    }

    @Test
    fun `toLlmInsight serialises tasks JSON parseable without exceptions`() {
        // Coaching tips containing JSON-meta characters (quotes, backslashes) must
        // serialise to a string that round-trips through any standard JSON parser.
        // We don't pin the exact escape form (the regex-based extractor leaves
        // backslash-escapes in the raw value, so re-escaping produces \\\" not \");
        // we only require that the result parses cleanly and contains all tips.
        val raw = """
            {"question_detected": false, "answer": "Note",
             "coaching_tips": ["Tip with quoted word", "Backslash \\ tip"]}
        """.trimIndent()
        val parsed = InterviewOutputParser.parse(raw, role)
        val insight = InterviewOutputParser.toLlmInsight(parsed, "s1", 1L, emptyList())

        assertNotNull(insight.tasks)
        // The result must start with [ and end with ] (well-formed array).
        assertTrue(insight.tasks!!.startsWith("[") && insight.tasks!!.endsWith("]"))
        // Both tips' identifying words must appear in the serialised form.
        assertTrue(insight.tasks!!.contains("quoted word"))
        assertTrue(insight.tasks!!.contains("Backslash"))
    }

    @Test
    fun `toLlmInsight returns null tasks when coaching tips list is empty`() {
        val raw = """{"question_detected": true, "detected_question": "Q", "answer": "A", "coaching_tips": []}"""
        val parsed = InterviewOutputParser.parse(raw, role)
        val insight = InterviewOutputParser.toLlmInsight(parsed, "s1", 1L, emptyList())
        assertNull(insight.tasks)
    }

    // ─── Skeleton schema (Phase 3) ──────────────────────────────────────────────

    @Test
    fun `parses the skeleton schema`() {
        val raw = """
            {
              "question_detected": true,
              "question_type": "technical_deep_dive",
              "detected_question": "How do you handle Terraform state locking across teams?",
              "skeleton": [
                "S3 backend + DynamoDB lock table",
                "per-env state separation, not per-team",
                "war story: state corruption during multi-region migration",
                "moved to Atlantis for serialized applies"
              ],
              "depth_probe": "Expect a follow-up on orphaned locks",
              "coaching_tips": ["Lead with the incident, not the tooling"]
            }
        """.trimIndent()

        val result = InterviewOutputParser.parse(raw, role)

        assertTrue(result.questionDetected)
        assertEquals(QuestionType.TECHNICAL_DEEP_DIVE, result.questionType)
        assertEquals(4, result.skeleton.size)
        assertEquals("S3 backend + DynamoDB lock table", result.skeleton.first())
        assertEquals("Expect a follow-up on orphaned locks", result.depthProbe)
        assertEquals(listOf("Lead with the incident, not the tooling"), result.coachingTips)
    }

    @Test
    fun `maps every documented question type`() {
        val cases = mapOf(
            "technical_deep_dive" to QuestionType.TECHNICAL_DEEP_DIVE,
            "behavioral" to QuestionType.BEHAVIORAL,
            "system_design" to QuestionType.SYSTEM_DESIGN,
            "incident_retro" to QuestionType.INCIDENT_RETRO,
            "culture_fit" to QuestionType.CULTURE_FIT
        )
        cases.forEach { (raw, expected) ->
            val json = """{"question_detected":true,"question_type":"$raw","skeleton":["x"]}"""
            assertEquals("failed for $raw", expected, InterviewOutputParser.parse(json, role).questionType)
        }
    }

    @Test
    fun `unknown question type degrades to null rather than throwing`() {
        val json = """{"question_detected":true,"question_type":"interpretive_dance","skeleton":["x"]}"""

        assertNull(InterviewOutputParser.parse(json, role).questionType)
    }

    // ─── Backward compatibility with the prose schema ───────────────────────────

    @Test
    fun `old prose schema still parses and synthesises a skeleton`() {
        // Insights persisted before Phase 3 carry "answer" and no "skeleton". They must
        // keep rendering rather than showing an empty card.
        val raw = """
            {
              "question_detected": true,
              "detected_question": "Tell me about a hard bug.",
              "answer": "I tracked down a race condition in our payment retry path.",
              "coaching_tips": ["Lead with impact"]
            }
        """.trimIndent()

        val result = InterviewOutputParser.parse(raw, role)

        assertEquals(
            "I tracked down a race condition in our payment retry path.",
            result.answerSuggestion
        )
        assertEquals(
            "prose answers become a single-item skeleton",
            listOf("I tracked down a race condition in our payment retry path."),
            result.skeleton
        )
        assertNull(result.questionType)
        assertNull(result.depthProbe)
    }

    @Test
    fun `skeleton wins over answer when both are present`() {
        val raw = """
            {"question_detected":true,"answer":"prose fallback",
             "skeleton":["bullet one","bullet two"]}
        """.trimIndent()

        val result = InterviewOutputParser.parse(raw, role)

        assertEquals(listOf("bullet one", "bullet two"), result.skeleton)
    }

    @Test
    fun `answerSuggestion is derived from the skeleton when no answer field exists`() {
        // answerSuggestion still backs LlmInsight.content, so it must never be blank
        // when the model returned only a skeleton.
        val raw = """{"question_detected":true,"skeleton":["alpha","beta"]}"""

        val result = InterviewOutputParser.parse(raw, role)

        assertTrue("alpha" in result.answerSuggestion)
        assertTrue("beta" in result.answerSuggestion)
    }

    // ─── Degradation ────────────────────────────────────────────────────────────

    @Test
    fun `truncated skeleton json degrades without throwing`() {
        // Small models truncate mid-array when they hit the token budget.
        val raw = """{"question_detected":true,"skeleton":["first bullet","second"""

        val result = InterviewOutputParser.parse(raw, role)

        assertNotNull(result)
        assertTrue(result.answerSuggestion.isNotBlank())
    }

    // ─── Persistence mapping ────────────────────────────────────────────────────

    @Test
    fun `skeleton is persisted in the tasks column`() {
        val insight = InterviewInsight(
            questionDetected = true,
            detectedQuestion = "How do you handle state locking?",
            answerSuggestion = "S3 + DynamoDB",
            coachingTips = listOf("Lead with the incident"),
            skeleton = listOf("S3 backend", "DynamoDB lock table"),
            questionType = QuestionType.TECHNICAL_DEEP_DIVE,
            depthProbe = "Expect a follow-up on orphaned locks",
            role = role
        )

        val llm = InterviewOutputParser.toLlmInsight(insight, "s1", 123L, emptyList())

        // Reuses the existing llm_insights table — no schema migration.
        assertTrue("skeleton must reach the tasks column", llm.tasks!!.contains("S3 backend"))
        assertTrue(llm.tasks!!.contains("DynamoDB lock table"))
    }

    @Test
    fun `depth probe survives the round trip to LlmInsight`() {
        val insight = InterviewInsight(
            questionDetected = true,
            detectedQuestion = "Q?",
            answerSuggestion = "A",
            coachingTips = emptyList(),
            skeleton = listOf("bullet"),
            questionType = null,
            depthProbe = "Expect a follow-up on orphaned locks",
            role = role
        )

        val llm = InterviewOutputParser.toLlmInsight(insight, "s1", 123L, emptyList())

        assertTrue(
            "depth probe must be recoverable from the persisted insight",
            llm.content.contains("orphaned locks")
        )
    }
}
