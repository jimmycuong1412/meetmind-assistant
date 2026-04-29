package com.meetmind.assistant.domain.usecase.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [InsightOutputParser] — the workhorse text-cleaning + field-extraction
 * helpers used by both the standard insights pipeline and Interview Mode.
 *
 * The parser is regex / scanner based (not [org.json]) so it can run in the
 * Android-free domain layer. These tests pin the exact behaviours that have
 * caused production bugs in the past:
 *
 *  - `<think>` blocks leaking into rendered insight cards
 *  - Code fences ` ```json ... ``` ` confusing field extraction
 *  - Unescaped quotes inside string values terminating extraction early
 *  - Action items with commas being split into multiple bogus items
 */
class InsightOutputParserTest {

    // ─── stripThinkingBlock ─────────────────────────────────────────────────────

    @Test
    fun `stripThinkingBlock removes complete think block`() {
        val input = "<think>Reasoning step 1. Reasoning step 2.</think>\nThe summary."
        assertEquals("The summary.", InsightOutputParser.stripThinkingBlock(input))
    }

    @Test
    fun `stripThinkingBlock leaves text unchanged when no think block`() {
        val input = "Just a plain summary."
        assertEquals(input, InsightOutputParser.stripThinkingBlock(input))
    }

    @Test
    fun `stripThinkingBlock discards everything after unclosed think tag`() {
        // Model was cut off mid-reasoning by the token budget — anything after the
        // unclosed <think> is partial reasoning, not a usable insight.
        val input = "Header.\n<think>Endless reasoning that never closes…"
        assertEquals("Header.", InsightOutputParser.stripThinkingBlock(input))
    }

    @Test
    fun `stripThinkingBlock keeps text on both sides of the block`() {
        val input = "Before.<think>middle</think>After."
        assertEquals("Before.After.", InsightOutputParser.stripThinkingBlock(input))
    }

    // ─── stripCodeFences ────────────────────────────────────────────────────────

    @Test
    fun `stripCodeFences removes json fence`() {
        val input = "```json\n{\"summary\": \"hello\"}\n```"
        assertEquals("""{"summary": "hello"}""", InsightOutputParser.stripCodeFences(input))
    }

    @Test
    fun `stripCodeFences removes plain fence`() {
        val input = "```\nplain content\n```"
        assertEquals("plain content", InsightOutputParser.stripCodeFences(input))
    }

    @Test
    fun `stripCodeFences leaves non-fenced text alone`() {
        val input = """{"summary": "no fence"}"""
        assertEquals(input, InsightOutputParser.stripCodeFences(input))
    }

    @Test
    fun `stripCodeFences handles uppercase JSON tag`() {
        val input = "```JSON\n{\"x\": 1}\n```"
        assertEquals("""{"x": 1}""", InsightOutputParser.stripCodeFences(input))
    }

    // ─── extractJsonField ───────────────────────────────────────────────────────

    @Test
    fun `extractJsonField pulls a simple string value`() {
        val input = """{"summary": "hello world", "tasks": []}"""
        assertEquals("hello world", InsightOutputParser.extractJsonField(input, "summary"))
    }

    @Test
    fun `extractJsonField returns null for missing field`() {
        val input = """{"summary": "x"}"""
        assertNull(InsightOutputParser.extractJsonField(input, "missing"))
    }

    @Test
    fun `extractJsonField handles inner unescaped quotes followed by content`() {
        // A small model emitted: "summary": "the "quoted" word in middle"
        // The closing quote of the value is the one followed by , or }.
        val input = """{"summary": "the "quoted" word in middle", "tasks": []}"""
        val result = InsightOutputParser.extractJsonField(input, "summary")
        // The scanner should treat the inner quotes as content (since they're not
        // followed by , or }) and only stop at the real terminator.
        assertEquals("""the quoted word in middle""", result)
    }

    @Test
    fun `extractJsonField handles backslash-escaped quotes`() {
        val input = """{"summary": "she said \"hi\""}"""
        assertEquals("""she said "hi"""", InsightOutputParser.extractJsonField(input, "summary"))
    }

    // ─── extractJsonArray ───────────────────────────────────────────────────────

    @Test
    fun `extractJsonArray pulls quoted string items`() {
        val input = """{"tasks": ["first", "second", "third"]}"""
        assertEquals(
            listOf("first", "second", "third"),
            InsightOutputParser.extractJsonArray(input, "tasks")
        )
    }

    @Test
    fun `extractJsonArray returns empty for missing field`() {
        val input = """{"tasks": []}"""
        assertEquals(emptyList<String>(), InsightOutputParser.extractJsonArray(input, "absent"))
    }

    @Test
    fun `extractJsonArray returns empty for empty array`() {
        val input = """{"tasks": []}"""
        assertEquals(emptyList<String>(), InsightOutputParser.extractJsonArray(input, "tasks"))
    }

    @Test
    fun `extractJsonArray keeps items containing commas as single entries`() {
        // Commas inside quoted strings must NOT split the item — this is the
        // bug that caused "Prepare report, review data" to become two entries.
        val input = """{"tasks": ["Prepare report, review data", "Send email"]}"""
        val result = InsightOutputParser.extractJsonArray(input, "tasks")
        assertEquals(2, result.size)
        assertEquals("Prepare report, review data", result[0])
        assertEquals("Send email", result[1])
    }

    @Test
    fun `extractJsonArray ignores blank items`() {
        val input = """{"tasks": ["valid", "", "  ", "another"]}"""
        val result = InsightOutputParser.extractJsonArray(input, "tasks")
        assertEquals(listOf("valid", "another"), result)
    }
}
