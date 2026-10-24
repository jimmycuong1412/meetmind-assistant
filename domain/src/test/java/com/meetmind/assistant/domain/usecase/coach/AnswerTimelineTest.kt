package com.meetmind.assistant.domain.usecase.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerTimelineTest {

    @Test
    fun `a new question ends the running answer`() {
        val first = AnswerTimeline.start(emptyMap(), "q1", atMs = 10_000)
        val second = AnswerTimeline.start(first, "q2", atMs = 70_000)

        assertEquals(60_000, second.getValue("q1").elapsedMs(nowMs = 200_000))
        assertFalse(second.getValue("q1").isRunning)
        assertTrue(second.getValue("q2").isRunning)
        assertEquals(30_000, second.getValue("q2").elapsedMs(nowMs = 100_000))
    }

    @Test
    fun `endAll freezes running answers and leaves finished ones alone`() {
        val timings = AnswerTimeline.start(AnswerTimeline.start(emptyMap(), "q1", 0), "q2", 40_000)

        val ended = AnswerTimeline.endAll(timings, atMs = 55_000)

        assertEquals(40_000, ended.getValue("q1").elapsedMs(nowMs = 999_999))
        assertEquals(15_000, ended.getValue("q2").elapsedMs(nowMs = 999_999))
        assertTrue(ended.values.none { it.isRunning })
    }

    @Test
    fun `short answer nudge applies only to finished answers under 30 seconds`() {
        val running = AnswerTiming(startedAtMs = 0)
        assertFalse(AnswerTimeline.isShortAnswer(running))
        assertTrue(AnswerTimeline.isShortAnswer(AnswerTiming(0, endedAtMs = 29_999)))
        assertFalse(AnswerTimeline.isShortAnswer(AnswerTiming(0, endedAtMs = 30_000)))
    }

    @Test
    fun `pace thresholds`() {
        assertEquals(AnswerTimeline.Pace.ON_TRACK, AnswerTimeline.pace(89_999))
        assertEquals(AnswerTimeline.Pace.LONG, AnswerTimeline.pace(90_000))
        assertEquals(AnswerTimeline.Pace.TOO_LONG, AnswerTimeline.pace(120_000))
    }

    @Test
    fun `elapsed never goes negative`() {
        assertEquals(0, AnswerTiming(startedAtMs = 50_000).elapsedMs(nowMs = 10_000))
    }
}
