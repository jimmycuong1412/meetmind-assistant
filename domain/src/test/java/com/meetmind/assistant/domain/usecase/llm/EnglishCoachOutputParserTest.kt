package com.meetmind.assistant.domain.usecase.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EnglishCoachOutputParser].
 *
 * Pure JVM tests — no Android deps.
 */
class EnglishCoachOutputParserTest {

    private val dailyCtx = "daily"
    private val proCtx   = "professional"

    // ─── Happy path ─────────────────────────────────────────────────────────────

    @Test
    fun `parses well-formed JSON with corrections and tips`() {
        val raw = """
            {
              "original": "I goes to the office yesterday.",
              "corrected": "I went to the office yesterday.",
              "is_correct": false,
              "polish": "I headed to the office yesterday.",
              "coaching_tip": "Use past tense 'went', not present 'goes', for past actions."
            }
        """.trimIndent()

        val result = EnglishCoachOutputParser.parse(raw, dailyCtx)

        assertEquals("I goes to the office yesterday.", result.original)
        assertEquals("I went to the office yesterday.", result.corrected)
        assertFalse(result.isCorrect)
        assertEquals("I headed to the office yesterday.", result.polish)
        assertEquals("Use past tense 'went', not present 'goes', for past actions.", result.coachingTip)
        assertEquals(dailyCtx, result.context)
    }

    @Test
    fun `parses is_correct true — no corrections needed`() {
        val raw = """
            {
              "original": "She has been working here for three years.",
              "corrected": "She has been working here for three years.",
              "is_correct": true,
              "polish": null,
              "coaching_tip": null
            }
        """.trimIndent()

        val result = EnglishCoachOutputParser.parse(raw, dailyCtx)

        assertTrue(result.isCorrect)
        assertNull(result.polish)
        assertNull(result.coachingTip)
    }

    // ─── is_correct variants ─────────────────────────────────────────────────────

    @Test
    fun `accepts string-quoted false for is_correct`() {
        val raw = """{"original":"X","corrected":"Y","is_correct":"false","polish":null,"coaching_tip":"tip"}"""
        assertFalse(EnglishCoachOutputParser.parse(raw, dailyCtx).isCorrect)
    }

    @Test
    fun `accepts string-quoted true for is_correct`() {
        val raw = """{"original":"X","corrected":"X","is_correct":"true","polish":null,"coaching_tip":null}"""
        assertTrue(EnglishCoachOutputParser.parse(raw, dailyCtx).isCorrect)
    }

    @Test
    fun `defaults is_correct to true when field is missing`() {
        val raw = """{"original":"X","corrected":"X","polish":null,"coaching_tip":null}"""
        assertTrue(EnglishCoachOutputParser.parse(raw, dailyCtx).isCorrect)
    }

    // ─── Code-fence and think-block stripping ────────────────────────────────────

    @Test
    fun `strips markdown code fences`() {
        val raw = """
            ```json
            {
              "original": "He don't know.",
              "corrected": "He doesn't know.",
              "is_correct": false,
              "polish": null,
              "coaching_tip": "Use 'doesn't' for third-person singular."
            }
            ```
        """.trimIndent()

        val result = EnglishCoachOutputParser.parse(raw, dailyCtx)
        assertEquals("He don't know.", result.original)
        assertFalse(result.isCorrect)
    }

    @Test
    fun `strips thinking block before parsing`() {
        val raw = """
            <think>Let me analyze the grammar...</think>
            {
              "original": "We was happy.",
              "corrected": "We were happy.",
              "is_correct": false,
              "polish": null,
              "coaching_tip": "Use 'were' for plural subjects."
            }
        """.trimIndent()

        val result = EnglishCoachOutputParser.parse(raw, proCtx)
        assertEquals("We was happy.", result.original)
        assertEquals("We were happy.", result.corrected)
    }

    // ─── Null / missing optional fields ─────────────────────────────────────────

    @Test
    fun `treats literal null string as absent for polish`() {
        val raw = """{"original":"A","corrected":"B","is_correct":false,"polish":"null","coaching_tip":"tip"}"""
        val result = EnglishCoachOutputParser.parse(raw, dailyCtx)
        assertNull(result.polish)
        assertNotNull(result.coachingTip)
    }

    @Test
    fun `treats blank string as absent for coaching_tip`() {
        val raw = """{"original":"A","corrected":"B","is_correct":false,"polish":"Better phrasing.","coaching_tip":""}"""
        val result = EnglishCoachOutputParser.parse(raw, dailyCtx)
        assertNotNull(result.polish)
        assertNull(result.coachingTip)
    }

    // ─── toLlmInsight mapping ────────────────────────────────────────────────────

    @Test
    fun `toLlmInsight encodes original in title capped at 80 chars`() {
        val longOriginal = "a".repeat(100)
        val insight = EnglishCoachOutputParser.parse(
            """{"original":"$longOriginal","corrected":"B","is_correct":false,"polish":null,"coaching_tip":null}""",
            dailyCtx
        )
        val llmInsight = EnglishCoachOutputParser.toLlmInsight(insight, "sid", 0L, emptyList())
        assertEquals(80, llmInsight.title?.length)
    }

    @Test
    fun `toLlmInsight encodes polish and coaching tip in tasks JSON array`() {
        val insight = EnglishCoachOutputParser.parse(
            """{"original":"A","corrected":"B","is_correct":false,"polish":"Better.","coaching_tip":"Why."}""",
            proCtx
        )
        val llmInsight = EnglishCoachOutputParser.toLlmInsight(insight, "sid", 0L, emptyList())
        assertNotNull(llmInsight.tasks)
        assertTrue(llmInsight.tasks!!.contains("Better."))
        assertTrue(llmInsight.tasks!!.contains("Why."))
    }

    @Test
    fun `toLlmInsight sets tasks to null when no polish or tip`() {
        val insight = EnglishCoachOutputParser.parse(
            """{"original":"A","corrected":"A","is_correct":true,"polish":null,"coaching_tip":null}""",
            dailyCtx
        )
        val llmInsight = EnglishCoachOutputParser.toLlmInsight(insight, "sid", 0L, emptyList())
        assertNull(llmInsight.tasks)
    }

    @Test
    fun `toLlmInsight stores context in questionType`() {
        val insight = EnglishCoachOutputParser.parse(
            """{"original":"A","corrected":"B","is_correct":false,"polish":null,"coaching_tip":"tip"}""",
            proCtx
        )
        val llmInsight = EnglishCoachOutputParser.toLlmInsight(insight, "sid", 0L, emptyList())
        assertEquals(proCtx, llmInsight.questionType)
    }

    // ─── Fallback on malformed JSON ──────────────────────────────────────────────

    @Test
    fun `falls back gracefully on completely malformed input`() {
        val raw = "The model returned plain text instead of JSON."
        val result = EnglishCoachOutputParser.parse(raw, dailyCtx)
        assertTrue(result.isCorrect) // fallback assumes no errors
        assertEquals(dailyCtx, result.context)
    }
}
