package com.meetmind.assistant.domain.usecase.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PhotoAnalysisQueue] — the FIFO serial worker that lets the user keep
 * capturing photos while earlier ones are still being analyzed. Jobs must run one
 * at a time in submission order; a failing job must not kill the worker.
 */
class PhotoAnalysisQueueTest {

    @Test
    fun `jobs run one at a time in submission order`() = runTest {
        val queue = PhotoAnalysisQueue()
        val worker = launch { queue.process() }
        val firstJobGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        queue.submit {
            events.add("first:start")
            firstJobGate.await()
            events.add("first:end")
        }
        queue.submit { events.add("second:start") }

        testScheduler.advanceUntilIdle()
        // Second job must not start while the first is suspended.
        assertEquals(listOf("first:start"), events)

        firstJobGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("first:start", "first:end", "second:start"), events)

        worker.cancelAndJoin()
    }

    @Test
    fun `pending counts submitted jobs and drops to zero when done`() = runTest {
        val queue = PhotoAnalysisQueue()
        val worker = launch { queue.process() }
        val gate = CompletableDeferred<Unit>()

        assertEquals(0, queue.pending.value)
        queue.submit { gate.await() }
        queue.submit { }
        assertEquals(2, queue.pending.value)

        testScheduler.advanceUntilIdle()
        assertEquals(2, queue.pending.value) // first still running, second waiting

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(0, queue.pending.value)

        worker.cancelAndJoin()
    }

    @Test
    fun `a throwing job does not stop later jobs and still decrements pending`() = runTest {
        val queue = PhotoAnalysisQueue()
        val worker = launch { queue.process() }
        var secondRan = false

        queue.submit { throw IllegalStateException("boom") }
        queue.submit { secondRan = true }
        testScheduler.advanceUntilIdle()

        assertTrue(secondRan)
        assertEquals(0, queue.pending.value)
        assertFalse(worker.isCancelled)

        worker.cancelAndJoin()
    }
}
