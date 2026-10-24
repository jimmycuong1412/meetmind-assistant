package com.meetmind.assistant.domain.usecase.ask

import com.meetmind.assistant.domain.model.LlmInsight
import com.meetmind.assistant.domain.model.SessionWithDetails
import com.meetmind.assistant.domain.model.TranscriptionSegment
import com.meetmind.assistant.domain.model.TranscriptionSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskContextBuilderTest {

    private fun session(
        id: String,
        name: String?,
        lines: List<Pair<String?, String>>,
        insights: List<String> = emptyList()
    ): SessionWithDetails {
        val createdAt = 1_000_000L
        return SessionWithDetails(
            session = TranscriptionSession(id = id, name = name, createdAt = createdAt, lastModifiedAt = createdAt),
            segments = lines.mapIndexed { i, (speaker, text) ->
                TranscriptionSegment(
                    id = "$id-s$i",
                    sessionId = id,
                    text = text,
                    timestamp = createdAt + i * 10_000L,
                    isComplete = true,
                    speaker = speaker
                )
            },
            insights = insights.mapIndexed { i, content ->
                LlmInsight(id = "$id-i$i", sessionId = id, content = content, timestamp = createdAt, sourceSegmentIds = emptyList())
            }
        )
    }

    @Test
    fun `groups consecutive segments of one speaker and breaks on speaker change`() {
        val passages = AskContextBuilder.buildPassages(
            listOf(
                session(
                    "a", "Sprint review",
                    listOf("Dora" to "The share bar blocks taps.", "Dora" to "I can take the fix.", "Marco" to "Agreed.")
                )
            )
        )

        assertEquals(2, passages.size)
        assertEquals("The share bar blocks taps. I can take the fix.", passages[0].text)
        assertEquals("Dora", passages[0].speaker)
        assertEquals("a-s0", passages[0].segmentId)
        assertEquals(0L, passages[0].offsetMs)
        assertEquals("Marco", passages[1].speaker)
        assertEquals(20_000L, passages[1].offsetMs)
    }

    @Test
    fun `ignores partial and blank segments`() {
        val details = session("a", null, listOf(null to "Kept line.")).let {
            it.copy(
                segments = it.segments + TranscriptionSegment("p", "a", "still talking", 2_000_000L, isComplete = false) +
                    TranscriptionSegment("b", "a", "   ", 2_000_001L, isComplete = true)
            )
        }

        val passages = AskContextBuilder.buildPassages(listOf(details))

        assertEquals(listOf("Kept line."), passages.map { it.text })
    }

    @Test
    fun `picks the passage that matches the question`() {
        val sessions = listOf(
            session(
                "a", "Sprint review",
                listOf(
                    "Alex" to "Lunch is at noon on Friday.",
                    "Dora" to "The browser blocker needs a real fix behind a flag.",
                    "Marco" to "Budget numbers are due next month."
                )
            )
        )

        val context = AskContextBuilder.build(sessions, "What did we decide about the browser blocker?", budgetChars = 120)

        assertEquals(1, context.passages.size)
        assertTrue(context.passages[0].text.contains("browser blocker"))
        assertTrue(context.truncated)
    }

    @Test
    fun `matches word forms by prefix`() {
        val sessions = listOf(
            session("a", null, listOf("A" to "We are migrating the database tonight.", "B" to "Coffee machine is broken."))
        )

        val context = AskContextBuilder.build(sessions, "When is the migration?", budgetChars = 100)

        assertEquals("We are migrating the database tonight.", context.passages.single().text)
    }

    @Test
    fun `generic question falls back to insights and samples every meeting`() {
        val sessions = listOf(
            session("a", "One", List(6) { "A" to "alpha line $it" }, insights = listOf("Summary of one")),
            session("b", "Two", List(6) { "B" to "beta line $it" })
        )

        val context = AskContextBuilder.build(sessions, "Summarise everything", budgetChars = 400)

        assertTrue(context.passages.any { it.isInsight })
        assertTrue(context.passages.any { it.sessionId == "a" && !it.isInsight })
        assertTrue(context.passages.any { it.sessionId == "b" })
    }

    @Test
    fun `orders excerpts by meeting then by position with summaries first`() {
        val sessions = listOf(
            session("a", "One", listOf("A" to "rollout plan", "B" to "rollout risk"), insights = listOf("rollout summary")),
            session("b", "Two", listOf("C" to "rollout date"))
        )

        val context = AskContextBuilder.build(sessions, "rollout")

        assertEquals(
            listOf("rollout summary", "rollout plan", "rollout risk", "rollout date"),
            context.passages.map { it.text }
        )
        assertFalse(context.truncated)
    }

    @Test
    fun `an oversized first passage is trimmed to fit instead of dropped`() {
        val long = "decision ".repeat(200).trim()
        val context = AskContextBuilder.build(listOf(session("a", null, listOf("A" to long))), "decision", budgetChars = 200)

        assertEquals(1, context.passages.size)
        assertTrue(context.passages[0].text.length <= 200)
    }

    @Test
    fun `no meetings gives an empty context`() {
        val context = AskContextBuilder.build(emptyList(), "anything")

        assertTrue(context.passages.isEmpty())
        assertFalse(context.truncated)
    }

    @Test
    fun `formats numbered excerpts with name, offset and speaker`() {
        val context = AskContextBuilder.build(
            listOf(session("a", null, listOf("Dora" to "Ship it.")), session("b", "Retro", listOf(null to "Ship it later."))),
            "ship"
        )

        val text = AskContextBuilder.formatExcerpts(context) { "Meeting ${it + 1}" }

        assertEquals(
            "[1] \"Meeting 1\" · 0:00 · Dora: Ship it.\n[2] \"Retro\" · 0:00: Ship it later.",
            text
        )
    }

    @Test
    fun `formats offsets above an hour`() {
        assertEquals("1:02:03", AskContextBuilder.formatOffset(3_723_000))
        assertEquals("14:32", AskContextBuilder.formatOffset(872_000))
    }
}
