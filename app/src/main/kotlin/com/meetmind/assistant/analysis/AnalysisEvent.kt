// T002 (spec 008): Sealed class for continuous conversation analysis events
package com.meetmind.assistant.analysis

/**
 * Priority order for tie-breaking when multiple event types match a window.
 * Lower ordinal = higher priority (DECISION > ACTION_ITEM > CONFUSION > QUESTION).
 */
enum class EventType {
    DECISION,
    ACTION_ITEM,
    CONFUSION,
    QUESTION;

    /** Returns true if this type has higher priority than [other]. */
    fun hasHigherPriorityThan(other: EventType): Boolean = ordinal < other.ordinal
}

/**
 * Outcome of one analysis tick emitted by [ConversationAnalyzer].
 * Exactly one subtype is emitted per [ConversationAnalyzer.analyze] call.
 *
 * @property text           The extracted text span that triggered this event (≤ 600 chars).
 * @property suggestionText LLM-generated follow-up suggestion; null when no model is loaded
 *                          or the event is [NoSignal].
 */
sealed class AnalysisEvent {

    /** No classifiable signal in the current transcript window — no card shown. */
    object NoSignal : AnalysisEvent()

    /**
     * The window contains an interrogative pattern; delegates to the existing
     * [com.meetmind.assistant.inference.CloudInferenceEngine] question path.
     */
    data class Question(
        val text: String,
        val suggestionText: String
    ) : AnalysisEvent()

    /**
     * The window contains an action-item pattern
     * (e.g., "Alice will finish the report by Friday").
     */
    data class ActionItem(
        val text: String,
        val suggestionText: String? = null
    ) : AnalysisEvent()

    /**
     * The window contains a decision pattern
     * (e.g., "We decided to go with the React approach").
     */
    data class Decision(
        val text: String,
        val suggestionText: String? = null
    ) : AnalysisEvent()

    /**
     * The window contains confusion signals
     * (e.g., "I'm not sure I follow what you mean").
     */
    data class Confusion(
        val text: String,
        val suggestionText: String? = null
    ) : AnalysisEvent()

    /** Returns the [EventType] for this event, or null for [NoSignal]. */
    fun eventType(): EventType? = when (this) {
        is NoSignal    -> null
        is Question    -> EventType.QUESTION
        is ActionItem  -> EventType.ACTION_ITEM
        is Decision    -> EventType.DECISION
        is Confusion   -> EventType.CONFUSION
    }
}
