// T011 (spec 008): Contract tests C4.1–C4.2 for DuplicateSuppressor
package com.meetmind.assistant.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [DuplicateSuppressor].
 *
 * C4.1 — Same leading-64-chars within 60s → second emission suppressed
 * C4.2 — Same text after 60s elapsed → second emission allowed
 */
class DuplicateSuppressorTest {

    // ── C4.1: duplicate within TTL is suppressed ─────────────────────────────

    @Test
    fun `C4-1 same text within 60s is suppressed after record`() {
        var fakeTime = 0L
        val suppressor = DuplicateSuppressor(clock = { fakeTime })

        val text = "We decided to go with the React approach for the frontend."

        assertFalse("Should not be suppressed before any record", suppressor.isSuppressed(text))

        suppressor.record(text)
        fakeTime = 30_000L  // 30s later — within TTL

        assertTrue("Should be suppressed within 60s", suppressor.isSuppressed(text))
    }

    @Test
    fun `C4-1b different text is not suppressed`() {
        var fakeTime = 0L
        val suppressor = DuplicateSuppressor(clock = { fakeTime })

        suppressor.record("Alice will finish the report by Friday")
        fakeTime = 10_000L

        assertFalse(
            "Different text should not be suppressed",
            suppressor.isSuppressed("Bob will present the findings next week")
        )
    }

    // ── C4.2: same text after TTL is allowed ──────────────────────────────────

    @Test
    fun `C4-2 same text after 60s elapsed is allowed through`() {
        var fakeTime = 0L
        val suppressor = DuplicateSuppressor(clock = { fakeTime })

        val text = "We decided to go with the React approach for the frontend."

        suppressor.record(text)
        fakeTime = 61_000L  // 61s later — past TTL

        assertFalse("Should not be suppressed after 60s", suppressor.isSuppressed(text))
    }

    // ── Normalisation: case and punctuation are ignored ───────────────────────

    @Test
    fun `normalisation ignores case and punctuation differences`() {
        var fakeTime = 0L
        val suppressor = DuplicateSuppressor(clock = { fakeTime })

        suppressor.record("We DECIDED to go with React!")
        fakeTime = 5_000L

        // Same words, different case/punctuation — should still be suppressed
        assertTrue(
            "Case/punctuation variants should be treated as duplicate",
            suppressor.isSuppressed("we decided to go with react")
        )
    }

    // ── Leading-64-char prefix matching ──────────────────────────────────────

    @Test
    fun `texts sharing first 64 chars are treated as duplicate`() {
        var fakeTime = 0L
        val suppressor = DuplicateSuppressor(clock = { fakeTime })

        val sharedPrefix = "a".repeat(64)
        val text1 = sharedPrefix + " extra words version one"
        val text2 = sharedPrefix + " completely different suffix here"

        suppressor.record(text1)
        fakeTime = 10_000L

        assertTrue(
            "Texts with identical 64-char prefix should be treated as duplicate",
            suppressor.isSuppressed(text2)
        )
    }

    @Test
    fun `texts differing in first 64 chars are not treated as duplicate`() {
        var fakeTime = 0L
        val suppressor = DuplicateSuppressor(clock = { fakeTime })

        suppressor.record("action item alice will finish report by friday afternoon")
        fakeTime = 10_000L

        assertFalse(
            "Texts with different 64-char prefix should not be suppressed",
            suppressor.isSuppressed("decision we agreed to use typescript for the entire project")
        )
    }
}
