package com.meetmind.assistant.domain.usecase.ask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAnswerParserTest {

    @Test
    fun `renumbers citations by first appearance`() {
        val parsed = AskAnswerParser.parse("Dora owns the fix [4]. Marco checks with support [2]. Flag first [4].", excerptCount = 5)

        assertEquals("Dora owns the fix [1]. Marco checks with support [2]. Flag first [1].", parsed.text)
        assertEquals(listOf(3, 1), parsed.citedExcerpts)
        assertFalse(parsed.notFound)
    }

    @Test
    fun `splits grouped markers and drops out of range numbers`() {
        val parsed = AskAnswerParser.parse("Both agreed [2, 9; 3].", excerptCount = 3)

        assertEquals("Both agreed [1][2].", parsed.text)
        assertEquals(listOf(1, 2), parsed.citedExcerpts)
    }

    @Test
    fun `removes a marker whose numbers are all invalid`() {
        val parsed = AskAnswerParser.parse("Nothing to cite [7].", excerptCount = 2)

        assertEquals("Nothing to cite.", parsed.text)
        assertTrue(parsed.citedExcerpts.isEmpty())
    }

    @Test
    fun `accepts worded markers`() {
        val parsed = AskAnswerParser.parse("Release is Friday [Excerpt 2].", excerptCount = 2)

        assertEquals("Release is Friday [1].", parsed.text)
        assertEquals(listOf(1), parsed.citedExcerpts)
    }

    @Test
    fun `detects not found and keeps the explanation`() {
        val parsed = AskAnswerParser.parse("NOT_FOUND: The excerpts only mention the blocker once [1].", excerptCount = 1)

        assertTrue(parsed.notFound)
        assertEquals("The excerpts only mention the blocker once [1].", parsed.text)
        assertEquals(listOf(0), parsed.citedExcerpts)
    }

    @Test
    fun `cuts a trailing sources list but keeps its citations`() {
        val parsed = AskAnswerParser.parse("Dora owns it.\n\nSources: [2]", excerptCount = 2)

        assertEquals("Dora owns it.", parsed.text)
        assertEquals(listOf(1), parsed.citedExcerpts)
    }

    @Test
    fun `strips answer prefix and code fences`() {
        val parsed = AskAnswerParser.parse("```\nAnswer: Yes [1].\n```", excerptCount = 1)

        assertEquals("Yes [1].", parsed.text)
    }

    @Test
    fun `streaming preview hides markers and the not found token`() {
        assertEquals("Dora owns the fix.", AskAnswerParser.streamingPreview("NOT_FOUND Dora owns the fix [1]."))
        assertEquals("Marco will check ", AskAnswerParser.streamingPreview("Marco will check [2"))
    }
}
