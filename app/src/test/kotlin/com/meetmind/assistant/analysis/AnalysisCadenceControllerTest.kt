// T012 (spec 008): Contract tests C2.1–C2.4 for AnalysisCadenceController
package com.meetmind.assistant.analysis

import com.meetmind.assistant.helpers.FakeConversationAnalyzer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [AnalysisCadenceController].
 *
 * Uses [StandardTestDispatcher] (NOT UnconfinedTestDispatcher) so [advanceTimeBy]
 * controls the virtual clock for delay()-based cadence firing.
 *
 * C2.1 — interval = 15s, session 45s → analyze() called exactly 3 times
 * C2.2 — if prior inference in-flight (mutex locked), tick is skipped
 * C2.3 — stop() called → no further ticks
 * C2.4 — minimum cadence enforced: interval < 10s is clamped to 10s
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisCadenceControllerTest {

    // ── C2.1: exact tick count ────────────────────────────────────────────────

    @Test
    fun `C2-1 fires exactly 3 times in 45s with 15s interval`() = runTest(StandardTestDispatcher()) {
        val fake = FakeConversationAnalyzer(eventSequence = listOf(AnalysisEvent.NoSignal))
        val buffer = TranscriptWindowBuffer().also {
            it.append(TimestampedSegment(0L, "some transcript"), windowSizeMs = 60_000L)
        }
        val controller = AnalysisCadenceController(
            analyzer = fake,
            buffer = buffer,
            intervalMs = 15_000L,
            windowSizeMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        advanceTimeBy(45_001L)
        controller.stop()

        assertEquals("Expected exactly 3 ticks in 45s at 15s interval", 3, fake.analyzeCallCount.get())
    }

    @Test
    fun `C2-1b fires 0 times before first interval elapses`() = runTest(StandardTestDispatcher()) {
        val fake = FakeConversationAnalyzer()
        val buffer = TranscriptWindowBuffer()
        val controller = AnalysisCadenceController(
            analyzer = fake,
            buffer = buffer,
            intervalMs = 20_000L,
            windowSizeMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        advanceTimeBy(19_999L)  // just under interval

        assertEquals("Should not fire before interval", 0, fake.analyzeCallCount.get())
        controller.stop()
    }

    // ── C2.2: in-flight skip (mutex) ──────────────────────────────────────────

    @Test
    fun `C2-2 tick is skipped when prior inference is in-flight`() = runTest(StandardTestDispatcher()) {
        var inflightCallCount = 0
        // Analyzer that blocks (simulates long inference) by tracking concurrent calls
        var concurrentCalls = 0
        var maxConcurrent = 0

        val blockingAnalyzer = ConversationAnalyzer { _ ->
            kotlinx.coroutines.flow.flow {
                concurrentCalls++
                maxConcurrent = maxOf(maxConcurrent, concurrentCalls)
                inflightCallCount++
                // Simulate long inference — 12s (longer than 10s interval)
                kotlinx.coroutines.delay(12_000L)
                concurrentCalls--
                emit(AnalysisEvent.NoSignal)
            }
        }

        val buffer = TranscriptWindowBuffer().also {
            it.append(TimestampedSegment(0L, "text"), windowSizeMs = 60_000L)
        }
        val controller = AnalysisCadenceController(
            analyzer = blockingAnalyzer,
            buffer = buffer,
            intervalMs = 10_000L,
            windowSizeMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        advanceTimeBy(31_000L)  // 3 ticks scheduled; first takes 12s → second tick skipped while first in-flight
        controller.stop()

        // Max concurrent should never exceed 1 (mutex prevents overlap)
        assertEquals("Concurrent inference calls must never exceed 1", 1, maxConcurrent)
    }

    // ── C2.3: stop() halts all ticks ─────────────────────────────────────────

    @Test
    fun `C2-3 stop halts cadence - no ticks after stop`() = runTest(StandardTestDispatcher()) {
        val fake = FakeConversationAnalyzer()
        val buffer = TranscriptWindowBuffer().also {
            it.append(TimestampedSegment(0L, "text"), windowSizeMs = 60_000L)
        }
        val controller = AnalysisCadenceController(
            analyzer = fake,
            buffer = buffer,
            intervalMs = 10_000L,
            windowSizeMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        advanceTimeBy(10_001L)   // first tick fires
        controller.stop()
        advanceTimeBy(30_000L)   // no more ticks after stop

        assertEquals("Only 1 tick should have fired before stop", 1, fake.analyzeCallCount.get())
    }

    @Test
    fun `C2-3b stop before start does not crash`() {
        val fake = FakeConversationAnalyzer()
        val buffer = TranscriptWindowBuffer()
        val controller = AnalysisCadenceController(
            analyzer = fake,
            buffer = buffer,
            intervalMs = 10_000L,
            windowSizeMs = 60_000L
        )
        controller.stop() // must not throw
    }

    // ── C2.4: minimum cadence clamp ───────────────────────────────────────────

    @Test
    fun `C2-4 interval below 10s is clamped to 10s minimum`() = runTest(StandardTestDispatcher()) {
        val fake = FakeConversationAnalyzer()
        val buffer = TranscriptWindowBuffer().also {
            it.append(TimestampedSegment(0L, "text"), windowSizeMs = 60_000L)
        }
        val controller = AnalysisCadenceController(
            analyzer = fake,
            buffer = buffer,
            intervalMs = 3_000L,  // ← below 10s minimum; effective interval should be 10s
            windowSizeMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        // Advance exactly to 10s — the clamped interval; exactly 1 tick should fire
        advanceTimeBy(10_001L)
        controller.stop()

        assertEquals("Should fire exactly once at clamped 10s interval", 1, fake.analyzeCallCount.get())
    }

    // ── General: empty buffer emits NoSignal without calling analyzer ─────────

    @Test
    fun `empty transcript buffer emits NoSignal without calling analyzer`() = runTest(StandardTestDispatcher()) {
        val fake = FakeConversationAnalyzer()
        val buffer = TranscriptWindowBuffer() // empty — no segments
        val controller = AnalysisCadenceController(
            analyzer = fake,
            buffer = buffer,
            intervalMs = 10_000L,
            windowSizeMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        advanceTimeBy(10_001L)
        controller.stop()

        assertEquals("Analyzer must not be called on empty buffer", 0, fake.analyzeCallCount.get())
    }
}
