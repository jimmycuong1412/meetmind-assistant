// T008 (spec 008): ConversationAnalyzer interface + DefaultConversationAnalyzer stub
package com.meetmind.assistant.analysis

import kotlinx.coroutines.flow.Flow

/**
 * Analyzes a transcript window and emits exactly one [AnalysisEvent].
 *
 * Contract (C1.1–C1.6 from contracts/contracts.md):
 * - Blank input → [AnalysisEvent.NoSignal], zero inference calls
 * - Non-blank input → keyword classify → LLM call (if non-NoSignal) → emit event
 * - Input is already trimmed to ≤ 600 chars by [TranscriptWindowBuffer.windowText]
 *
 * Injectable: production uses [DefaultConversationAnalyzer];
 * tests inject [com.meetmind.assistant.helpers.FakeConversationAnalyzer].
 */
fun interface ConversationAnalyzer {
    /**
     * Analyzes [windowText] and returns a [Flow] that emits exactly one [AnalysisEvent].
     *
     * @param windowText Transcript window text, ≤ 600 chars (enforced by [TranscriptWindowBuffer]).
     */
    fun analyze(windowText: String): Flow<AnalysisEvent>
}
