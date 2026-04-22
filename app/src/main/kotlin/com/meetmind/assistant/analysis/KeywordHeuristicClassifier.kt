// T007 (spec 008): Lightweight keyword-matching fallback classifier
package com.meetmind.assistant.analysis

/**
 * Classifies a transcript window text into an [EventType] using compiled regex patterns.
 *
 * Used as the first step in [DefaultConversationAnalyzer] before any LLM call.
 * Returns null ([NoSignal]) when no pattern matches — approximately 80% of windows
 * in a typical meeting (silence, filler, side conversation).
 *
 * Pattern design (research.md Q6):
 * - ActionItem: modal-verb anchors indicating future commitment
 * - Decision: past-tense agreement verbs indicating a resolved choice
 * - Confusion: first-person epistemic negation indicating misunderstanding
 * - Question: interrogative markers (end with "?", wh-words, rising markers)
 *
 * Priority order when multiple patterns match: DECISION > ACTION_ITEM > CONFUSION > QUESTION
 * (see [EventType] ordinal ordering).
 */
object KeywordHeuristicClassifier {

    private val ACTION_ITEM_REGEX = Regex(
        """(?i)\b(will\s+(do|finish|complete|handle|send|review|schedule|write|update|check)|""" +
        """needs?\s+to|let'?s\s+make\s+sure|action\s+item|by\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|next\s+week|end\s+of)|""" +
        """(i|we|they|you|he|she)\s+will\s+\w+)\b"""
    )

    private val DECISION_REGEX = Regex(
        """(?i)\b(we\s+(decided|agreed|chose|picked|selected|resolved|concluded|settled\s+on)|""" +
        """let'?s\s+go\s+with|going\s+with|agreed\s+on|the\s+decision\s+is|""" +
        """we'?re\s+(going\s+with|using|choosing|adopting)|""" +
        """(decided|agreed|resolved|concluded)\s+to)\b"""
    )

    private val CONFUSION_REGEX = Regex(
        """(?i)\b(not\s+sure\s+(i\s+follow|about|what|how|why|if)|""" +
        """(i'?m|i\s+am)\s+(confused|lost|unclear)|""" +
        """(what|can\s+you)\s+(do\s+you\s+mean|does\s+that\s+mean|did\s+you\s+mean|clarify)|""" +
        """don'?t\s+(follow|understand)|""" +
        """could\s+you\s+(clarify|explain|repeat)|""" +
        """(that'?s|it'?s)\s+(unclear|confusing))\b"""
    )

    private val QUESTION_REGEX = Regex(
        """(?i)((\?+\s*$)|^\s*(what|who|where|when|why|how|is|are|was|were|do|does|did|can|could|would|should|shall|will|have|has|had)\b)"""
    )

    /**
     * Classifies [text] into an [EventType] or returns null if no signal is detected.
     *
     * @param text Transcript window text (≤ 600 chars, already trimmed by caller).
     * @return The highest-priority matching [EventType], or null for NoSignal.
     */
    fun classify(text: String): EventType? {
        if (text.isBlank()) return null

        // Check in priority order: DECISION > ACTION_ITEM > CONFUSION > QUESTION
        return when {
            DECISION_REGEX.containsMatchIn(text)    -> EventType.DECISION
            ACTION_ITEM_REGEX.containsMatchIn(text)  -> EventType.ACTION_ITEM
            CONFUSION_REGEX.containsMatchIn(text)    -> EventType.CONFUSION
            QUESTION_REGEX.containsMatchIn(text)     -> EventType.QUESTION
            else                                     -> null
        }
    }
}
