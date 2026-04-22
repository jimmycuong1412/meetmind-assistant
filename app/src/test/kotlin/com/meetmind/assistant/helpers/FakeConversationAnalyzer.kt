// T004 (spec 008): Test double for ConversationAnalyzer
package com.meetmind.assistant.helpers

import com.meetmind.assistant.analysis.AnalysisEvent
import com.meetmind.assistant.analysis.ConversationAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Configurable fake for [ConversationAnalyzer] used in contract tests.
 *
 * @param eventSequence  Ordered list of events to emit on successive [analyze] calls.
 *                       Loops on the last element if calls exceed the list length.
 *                       Defaults to a single [AnalysisEvent.NoSignal].
 */
class FakeConversationAnalyzer(
    private val eventSequence: List<AnalysisEvent> = listOf(AnalysisEvent.NoSignal)
) : ConversationAnalyzer {

    /** Number of times [analyze] has been called. Thread-safe. */
    val analyzeCallCount: AtomicInteger = AtomicInteger(0)

    /** The last window text passed to [analyze]. Empty string before first call. */
    var lastWindowText: String = ""
        private set

    override fun analyze(windowText: String): Flow<AnalysisEvent> {
        val callIndex = analyzeCallCount.getAndIncrement()
        lastWindowText = windowText
        val event = if (callIndex < eventSequence.size) {
            eventSequence[callIndex]
        } else {
            eventSequence.last()
        }
        return flow { emit(event) }
    }
}
