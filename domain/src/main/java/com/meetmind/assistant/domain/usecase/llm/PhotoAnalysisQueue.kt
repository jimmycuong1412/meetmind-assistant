package com.meetmind.assistant.domain.usecase.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * FIFO serial worker for photo-analysis jobs.
 *
 * llama.cpp is not thread-safe, so photos captured while an earlier one is still
 * being analyzed cannot run in parallel — they queue here and run strictly one at
 * a time in capture order. The owner launches [process] once in its scope
 * (MainViewModel uses viewModelScope) and mirrors [pending] into UI state.
 *
 * A job that throws is dropped (the job itself is expected to surface its own
 * error state); the worker keeps consuming subsequent jobs.
 */
class PhotoAnalysisQueue {

    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val _pending = MutableStateFlow(0)

    /** Jobs submitted but not yet finished, including the one currently running. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    /** Appends [job] to the queue; never blocks. */
    fun submit(job: suspend () -> Unit) {
        _pending.update { it + 1 }
        // UNLIMITED channel: trySend cannot fail while the channel is open.
        jobs.trySend(job)
    }

    /** Worker loop — runs queued jobs one at a time in submission order. */
    suspend fun process() {
        for (job in jobs) {
            try {
                job()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Job already surfaced its own failure; keep the worker alive.
            } finally {
                _pending.update { it - 1 }
            }
        }
    }
}
