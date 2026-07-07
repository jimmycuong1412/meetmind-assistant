package com.meetmind.assistant.domain.usecase.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PhotoContextQueue] — the pending photo-description buffer consumed
 * by SyncSttLlmUseCase at each insight tick.
 */
class PhotoContextQueueTest {

    @Test
    fun `drain returns queued descriptions in capture order and clears the queue`() {
        val queue = PhotoContextQueue()
        queue.add("whiteboard with Q3 roadmap")
        queue.add("slide showing budget table")

        val drained = queue.drain()

        assertEquals(listOf("whiteboard with Q3 roadmap", "slide showing budget table"), drained)
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun `drain on empty queue returns empty list`() {
        assertEquals(emptyList<String>(), PhotoContextQueue().drain())
    }

    @Test
    fun `blank descriptions are ignored`() {
        val queue = PhotoContextQueue()
        queue.add("   ")
        queue.add("")
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun `queue caps at MAX_PENDING keeping the newest entries`() {
        val queue = PhotoContextQueue()
        repeat(PhotoContextQueue.MAX_PENDING + 3) { i -> queue.add("photo $i") }

        val drained = queue.drain()

        assertEquals(PhotoContextQueue.MAX_PENDING, drained.size)
        // Oldest overflow entries (photo 0..2) were dropped.
        assertEquals("photo 3", drained.first())
        assertEquals("photo ${PhotoContextQueue.MAX_PENDING + 2}", drained.last())
    }

    @Test
    fun `formatBlock renders numbered photo lines`() {
        val block = PhotoContextQueue.formatBlock(listOf("a whiteboard", "a slide"))
        assertEquals(
            "Photos captured during this period:\nPhoto 1: a whiteboard\nPhoto 2: a slide",
            block
        )
    }

    @Test
    fun `formatBlock of empty list is empty string`() {
        assertEquals("", PhotoContextQueue.formatBlock(emptyList()))
    }
}
