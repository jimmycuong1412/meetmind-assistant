package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.RecognitionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test double for SherpaOnnxDataSource.
 *
 * Provides configurable RecognitionResult sequences and tracks decode concurrency
 * for contract C1.1 (decodeMutex prevents concurrent decode calls).
 *
 * spec 009 — T009
 */
class FakeSherpaOnnxDataSource(
    /** Called synchronously inside each fake decode invocation; can simulate mutex behaviour. */
    private val onDecode: (suspend (FloatArray) -> RecognitionResult)? = null,
    /** Fixed list of results to emit from startRecording(), in order. */
    private val results: List<RecognitionResult> = emptyList()
) {

    val decodeCallCount = AtomicInteger(0)

    /** Tracks the maximum number of concurrent decode() invocations observed. */
    var maxConcurrentDecodes: Int = 0
        private set

    private val concurrentCount = AtomicInteger(0)
    private val decodeMutex = Mutex()

    /**
     * Simulates a single decode() call; serialised by [decodeMutex].
     * Used in C1.1 concurrency tests.
     */
    suspend fun invokeDecodeOnce(audio: FloatArray): RecognitionResult {
        decodeCallCount.incrementAndGet()
        return decodeMutex.withLock {
            val current = concurrentCount.incrementAndGet()
            if (current > maxConcurrentDecodes) maxConcurrentDecodes = current
            try {
                onDecode?.invoke(audio) ?: RecognitionResult("", isComplete = true)
            } finally {
                concurrentCount.decrementAndGet()
            }
        }
    }

    /** Emits the pre-configured [results] list from a cold Flow (for C1.2 / C1.3 tests). */
    fun startRecording(): Flow<RecognitionResult> = flow {
        results.forEach { emit(it) }
    }
}
