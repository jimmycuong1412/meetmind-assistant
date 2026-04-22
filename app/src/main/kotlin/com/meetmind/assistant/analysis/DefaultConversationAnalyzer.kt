// T019 (spec 008): DefaultConversationAnalyzer — keyword classify → LLM suggestion
package com.meetmind.assistant.analysis

import android.util.Log
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.SessionMode
import com.meetmind.assistant.inference.CloudStreamingProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Production [ConversationAnalyzer] implementation.
 *
 * Two-step pipeline (research.md Q4):
 * 1. [KeywordHeuristicClassifier.classify] — ~0ms, filters ~80% of windows as NoSignal
 * 2. Single LLM call via [inferenceEngine] — only for non-NoSignal classifications
 *
 * Data minimisation (FR-004): [windowText] is already ≤ 600 chars from [TranscriptWindowBuffer].
 * This class additionally `.take(MAX_INPUT_CHARS)` as a defensive guard.
 *
 * @param inferenceEngine   Injectable provider for LLM suggestions (tests use [FakeInferenceEngine]).
 * @param duplicateSuppressor Suppresses duplicate cards within 60s.
 * @param isModelLoaded     Returns true if on-device model is ready; controls label-only fallback (FR-014).
 */
class DefaultConversationAnalyzer(
    private val inferenceEngine: CloudStreamingProvider,
    private val duplicateSuppressor: DuplicateSuppressor = DuplicateSuppressor(),
    private val isModelLoaded: () -> Boolean = { true }
) : ConversationAnalyzer {

    override fun analyze(windowText: String): Flow<AnalysisEvent> = flow {
        // Fast path: blank input → NoSignal, zero inference calls (C1.1)
        if (windowText.isBlank()) {
            emit(AnalysisEvent.NoSignal)
            return@flow
        }

        // Defensive truncation — buffer should have already done this, but guard anyway (C1.6)
        val safeText = windowText.take(MAX_INPUT_CHARS)

        // Step 1: keyword heuristic classification
        val eventType = KeywordHeuristicClassifier.classify(safeText)
        if (eventType == null) {
            Log.v(TAG, "NoSignal — no keyword match in window")
            emit(AnalysisEvent.NoSignal)
            return@flow
        }

        Log.d(TAG, "Classified as $eventType — generating suggestion")

        // Step 2: if no model loaded → label-only card (FR-014)
        if (!isModelLoaded()) {
            Log.d(TAG, "No model loaded — emitting label-only $eventType card")
            emit(labelOnlyEvent(eventType, safeText))
            return@flow
        }

        // Step 3: build type-specific system prompt and call inference (T020)
        val systemPrompt = buildSystemPrompt(eventType)
        val request = CloudInferenceRequest(
            requestId = UUID.randomUUID().toString(),
            questionText = safeText,
            systemPrompt = systemPrompt,
            provider = CloudProvider.GEMINI,
            sessionMode = SessionMode.MEETING
        )

        // Collect tokens and assemble suggestion
        val tokens = StringBuilder()
        inferenceEngine.streamSuggestion(request).collect { event ->
            when (event) {
                is InferenceEvent.Token    -> tokens.append(event.text)
                is InferenceEvent.Complete -> { /* tokens already accumulated */ }
                is InferenceEvent.Error    -> {
                    Log.w(TAG, "Inference error during analysis: ${event.message}")
                    emit(labelOnlyEvent(eventType, safeText))
                    return@collect
                }
                is InferenceEvent.FallbackActivated -> {
                    Log.d(TAG, "Fallback activated: ${event.reason}")
                }
            }
        }

        val suggestion = tokens.toString().trim()
        val analysisEvent = buildEvent(eventType, safeText, suggestion.ifBlank { null })

        // Duplicate suppression (FR-011)
        val fingerprint = suggestion.ifBlank { safeText }
        if (duplicateSuppressor.isSuppressed(fingerprint)) {
            Log.d(TAG, "Duplicate suppressed for $eventType")
            emit(AnalysisEvent.NoSignal)
            return@flow
        }
        duplicateSuppressor.record(fingerprint)

        emit(analysisEvent)
    }

    /**
     * T020: Per-event-type system prompts, each ≤ 320 chars.
     * Tailored to the conversational signal detected in the window.
     */
    fun buildSystemPrompt(eventType: EventType): String = when (eventType) {
        EventType.ACTION_ITEM ->
            "You are a meeting assistant. An action item was mentioned. " +
            "In 1-2 sentences, ask: who owns it and what is the deadline? Be concise."

        EventType.DECISION ->
            "You are a meeting assistant. A decision was made. " +
            "In 1 sentence, suggest documenting it or confirm the rationale. Be concise."

        EventType.CONFUSION ->
            "You are a meeting assistant. Someone expressed confusion. " +
            "In 1-2 sentences, suggest a clarifying question or simpler explanation. Be concise."

        EventType.QUESTION ->
            "You are a helpful assistant in a professional meeting. " +
            "Answer the question concisely and directly in 1-2 sentences."
    }

    companion object {
        private const val TAG = "ConversationAnalyzer"
        private const val MAX_INPUT_CHARS = 600
    }
}

/** Builds a label-only [AnalysisEvent] (no suggestion text) for the given [EventType]. */
private fun labelOnlyEvent(eventType: EventType, text: String): AnalysisEvent = when (eventType) {
    EventType.ACTION_ITEM -> AnalysisEvent.ActionItem(text = text, suggestionText = null)
    EventType.DECISION    -> AnalysisEvent.Decision(text = text, suggestionText = null)
    EventType.CONFUSION   -> AnalysisEvent.Confusion(text = text, suggestionText = null)
    EventType.QUESTION    -> AnalysisEvent.Question(text = text, suggestionText = "")
}

/** Builds a fully populated [AnalysisEvent] with [suggestion]. */
private fun buildEvent(eventType: EventType, text: String, suggestion: String?): AnalysisEvent =
    when (eventType) {
        EventType.ACTION_ITEM -> AnalysisEvent.ActionItem(text = text, suggestionText = suggestion)
        EventType.DECISION    -> AnalysisEvent.Decision(text = text, suggestionText = suggestion)
        EventType.CONFUSION   -> AnalysisEvent.Confusion(text = text, suggestionText = suggestion)
        EventType.QUESTION    -> AnalysisEvent.Question(text = text, suggestionText = suggestion ?: "")
    }
