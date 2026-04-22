// T006 (spec 008): Contract tests C5.1–C5.2 for TranscriptWindowBuffer
package com.meetmind.assistant.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [TranscriptWindowBuffer].
 *
 * C5.1 — Segments older than windowSizeMs are excluded from windowText()
 * C5.2 — Empty buffer returns ""
 * C5.3 — windowText() never exceeds 600 chars (data minimisation FR-004)
 */
class TranscriptWindowBufferTest {

    // ── C5.2: empty buffer ────────────────────────────────────────────────────

    @Test
    fun `C5-2 empty buffer returns empty string`() {
        val buffer = TranscriptWindowBuffer()
        assertEquals("", buffer.windowText(windowSizeMs = 60_000L))
    }

    // ── C5.1: segments older than window are excluded ─────────────────────────

    @Test
    fun `C5-1 segments older than windowSizeMs are excluded`() {
        val buffer = TranscriptWindowBuffer()
        val now = 1_000_000L

        // Old segment — 90s before "now"
        buffer.append(TimestampedSegment(now - 90_000L, "old text"), windowSizeMs = 60_000L)
        // Recent segment — 30s before "now"
        buffer.append(TimestampedSegment(now - 30_000L, "recent text"), windowSizeMs = 60_000L)
        // Current segment at "now"
        buffer.append(TimestampedSegment(now, "current text"), windowSizeMs = 60_000L)

        val result = buffer.windowText(windowSizeMs = 60_000L)

        assertTrue("Should contain recent text", result.contains("recent text"))
        assertTrue("Should contain current text", result.contains("current text"))
        assertTrue("Should NOT contain old text (>60s ago)", !result.contains("old text"))
    }

    @Test
    fun `C5-1b all segments within window are included`() {
        val buffer = TranscriptWindowBuffer()
        val now = 1_000_000L

        buffer.append(TimestampedSegment(now - 50_000L, "alpha"), windowSizeMs = 60_000L)
        buffer.append(TimestampedSegment(now - 20_000L, "beta"), windowSizeMs = 60_000L)
        buffer.append(TimestampedSegment(now, "gamma"), windowSizeMs = 60_000L)

        val result = buffer.windowText(windowSizeMs = 60_000L)

        assertTrue(result.contains("alpha"))
        assertTrue(result.contains("beta"))
        assertTrue(result.contains("gamma"))
    }

    // ── C5.3: truncation to MAX_CHARS ─────────────────────────────────────────

    @Test
    fun `C5-3 windowText returns at most 600 chars`() {
        val buffer = TranscriptWindowBuffer()
        val now = 1_000_000L

        // Add a segment whose text would exceed 600 chars combined
        repeat(20) { i ->
            buffer.append(
                TimestampedSegment(now - (20 - i) * 1_000L, "word$i ".repeat(5)),
                windowSizeMs = 60_000L
            )
        }

        val result = buffer.windowText(windowSizeMs = 60_000L)
        assertTrue(
            "windowText must be ≤ 600 chars (was ${result.length})",
            result.length <= TranscriptWindowBuffer.MAX_CHARS
        )
    }

    @Test
    fun `C5-3b truncation keeps most recent content`() {
        val buffer = TranscriptWindowBuffer()
        val now = 1_000_000L

        // Fill with old filler text then a distinct recent segment
        repeat(15) { i ->
            buffer.append(
                TimestampedSegment(now - (15 - i) * 2_000L, "filler ".repeat(6)),
                windowSizeMs = 60_000L
            )
        }
        buffer.append(TimestampedSegment(now, "RECENT_MARKER"), windowSizeMs = 60_000L)

        val result = buffer.windowText(windowSizeMs = 60_000L)
        assertTrue("Most recent content must be preserved", result.contains("RECENT_MARKER"))
    }

    // ── General: size() and clear() ───────────────────────────────────────────

    @Test
    fun `size returns correct count after appends`() {
        val buffer = TranscriptWindowBuffer()
        val now = 1_000_000L
        buffer.append(TimestampedSegment(now, "a"), windowSizeMs = 60_000L)
        buffer.append(TimestampedSegment(now + 1_000, "b"), windowSizeMs = 60_000L)
        assertEquals(2, buffer.size())
    }

    @Test
    fun `clear removes all segments`() {
        val buffer = TranscriptWindowBuffer()
        val now = 1_000_000L
        buffer.append(TimestampedSegment(now, "text"), windowSizeMs = 60_000L)
        buffer.clear()
        assertEquals(0, buffer.size())
        assertEquals("", buffer.windowText(windowSizeMs = 60_000L))
    }
}
