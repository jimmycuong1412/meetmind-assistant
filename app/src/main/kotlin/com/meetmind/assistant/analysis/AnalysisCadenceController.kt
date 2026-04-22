// T013 (spec 008): Sliding-window cadence controller for continuous conversation analysis
package com.meetmind.assistant.analysis

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives the continuous analysis cadence: fires every [intervalMs] (clamped to ≥ [MIN_INTERVAL_MS]),
 * reads the current [TranscriptWindowBuffer] window, and dispatches [ConversationAnalyzer.analyze].
 *
 * Design decisions (research.md):
 * - `delay()` loop inside `while(isActive)` — fully controllable with `advanceTimeBy` in tests
 * - `Mutex.tryLock()` debounce — skips tick if prior inference is still in-flight (never queues)
 * - `dispatcher` is injectable so tests pass `StandardTestDispatcher` for virtual-time control
 *
 * @param analyzer      Injectable [ConversationAnalyzer]; tests use [FakeConversationAnalyzer].
 * @param buffer        Rolling transcript buffer whose [TranscriptWindowBuffer.windowText] feeds analysis.
 * @param intervalMs    Desired cadence in milliseconds. Clamped to ≥ [MIN_INTERVAL_MS].
 * @param windowSizeMs  Transcript window passed to [TranscriptWindowBuffer.windowText].
 * @param dispatcher    Coroutine dispatcher for the ticker coroutine. Injectable for tests.
 */
class AnalysisCadenceController(
    private val analyzer: ConversationAnalyzer,
    private val buffer: TranscriptWindowBuffer,
    intervalMs: Long,
    private val windowSizeMs: Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /** Effective interval — enforces minimum cadence floor (FR-007). */
    private val effectiveIntervalMs: Long = maxOf(intervalMs, MIN_INTERVAL_MS)

    private val analysisMutex = Mutex()
    private var tickerJob: Job? = null

    /** Emits [AnalysisEvent]s produced by analysis ticks. Collect in [SessionViewModel]. */
    private val _events = MutableSharedFlow<AnalysisEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AnalysisEvent> = _events

    /**
     * Starts the cadence ticker in [scope]. Idempotent — calling [start] while already
     * running does nothing.
     */
    fun start(scope: CoroutineScope) {
        if (tickerJob?.isActive == true) return
        Log.d(TAG, "Starting cadence ticker: interval=${effectiveIntervalMs}ms window=${windowSizeMs}ms")
        tickerJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(effectiveIntervalMs)
                runTick()
            }
        }
    }

    /**
     * Stops the cadence ticker. Idempotent — safe to call before [start] or multiple times.
     */
    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        Log.d(TAG, "Cadence ticker stopped")
    }

    /**
     * Executes one analysis tick. Skips if a prior inference is still in-flight ([Mutex.tryLock]).
     * Skips if the transcript window is empty.
     */
    private suspend fun runTick() {
        if (!analysisMutex.tryLock()) {
            Log.d(TAG, "Tick skipped — prior inference still in-flight")
            return
        }
        try {
            val windowText = buffer.windowText(windowSizeMs)
            if (windowText.isBlank()) {
                Log.v(TAG, "Tick skipped — empty transcript window")
                return
            }
            Log.v(TAG, "Analysis tick — window=${windowText.length} chars")
            analyzer.analyze(windowText).collect { event ->
                Log.d(TAG, "Analysis event: ${event::class.simpleName}")
                _events.emit(event)
            }
        } finally {
            analysisMutex.unlock()
        }
    }

    companion object {
        private const val TAG = "CadenceController"
        /** Minimum allowed cadence — prevents cascade of parallel inference calls (FR-007). */
        const val MIN_INTERVAL_MS = 10_000L
    }
}
