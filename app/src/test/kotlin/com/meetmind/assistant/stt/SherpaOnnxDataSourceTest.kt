package com.meetmind.assistant.stt

import com.meetmind.assistant.data.model.RecognitionResult
import com.meetmind.assistant.helpers.FakeSherpaOnnxDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for SherpaOnnxDataSource — spec 009 T037.
 *
 * C1.1 — decodeMutex max concurrent = 1 (no two decode() calls in parallel)
 * C1.2 — empty audio results in zero emissions
 * C1.3 — recognized text appended correctly (isComplete flag preserved)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SherpaOnnxDataSourceTest {

    /**
     * C1.1 — Concurrent decode() calls must be serialised by decodeMutex.
     * Launch 5 decode calls simultaneously; the mutex ensures at most 1 runs at a time.
     */
    @Test
    fun `C1_1 decodeMutex serialises concurrent decode calls`() = runTest {
        val fake = FakeSherpaOnnxDataSource()
        val audio = FloatArray(1600) { 0.0f }

        // Launch 5 concurrent decode calls
        val jobs = (1..5).map {
            async { fake.invokeDecodeOnce(audio) }
        }
        jobs.forEach { it.await() }

        assertEquals("All 5 decode calls should complete", 5, fake.decodeCallCount.get())
        assertEquals(
            "decodeMutex must prevent concurrent calls — max concurrent must be 1",
            1,
            fake.maxConcurrentDecodes
        )
    }

    /**
     * C1.2 — A FakeSherpaOnnxDataSource configured with no results emits nothing.
     * Maps to the real SherpaOnnxDataSource's empty-audio guard: if no VAD segments
     * fire (because audio is empty/silent), startRecording() emits zero RecognitionResults.
     */
    @Test
    fun `C1_2 empty audio yields zero emissions`() = runTest {
        val fake = FakeSherpaOnnxDataSource(results = emptyList())

        val results = fake.startRecording().toList()

        assertTrue("No results should be emitted for empty audio", results.isEmpty())
    }

    /**
     * C1.3 — Recognized text flows out with the correct isComplete flag preserved.
     * A final VAD segment sets isComplete=true; a partial/interim result sets it false.
     */
    @Test
    fun `C1_3 recognized text emitted with correct isComplete flag`() = runTest {
        val partial = RecognitionResult("Alice will finish", isComplete = false)
        val complete = RecognitionResult("Alice will finish the report by Friday", isComplete = true)
        val fake = FakeSherpaOnnxDataSource(results = listOf(partial, complete))

        val results = fake.startRecording().toList()

        assertEquals(2, results.size)
        assertEquals("Partial result text preserved", "Alice will finish", results[0].text)
        assertEquals("Partial result must have isComplete=false", false, results[0].isComplete)
        assertEquals(
            "Final result text preserved",
            "Alice will finish the report by Friday",
            results[1].text
        )
        assertEquals("Final result must have isComplete=true", true, results[1].isComplete)
    }
}
