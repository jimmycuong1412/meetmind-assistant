package com.meetmind.assistant.domain.usecase.llm

/**
 * Pure-function filler word counter for Interview Mode coaching.
 *
 * Detects common English filler words in transcribed text using lightweight
 * string matching — no ML model, no LLM inference required. Runs in < 1 ms
 * per segment and imposes zero inference budget cost.
 *
 * ## Filler word list
 * Single-word: um, uh, like, basically, literally, so
 * Multi-word:  you know  (detected via substring scan before tokenisation)
 *
 * ## Matching strategy
 * 1. Multi-word fillers are counted first via substring scan on the lowercased,
 *    de-punctuated text, then their constituent words are removed to prevent
 *    double-counting (e.g. "you know" must not also count "you").
 * 2. Remaining text is tokenised by whitespace and matched against single-word fillers.
 *
 * ## Scope
 * Only called on *completed* (isComplete == true) segments. Partial segments are
 * excluded to avoid counting half-spoken words that may be corrected by the STT engine.
 */
object FillerWordCounter {

    private val MULTI_WORD_FILLERS = listOf("you know")
    private val SINGLE_WORD_FILLERS = setOf("um", "uh", "like", "basically", "literally", "so")

    /**
     * Count filler words in [text].
     *
     * @param text Transcribed text from a completed segment. May contain punctuation.
     * @return Total filler word instances found (multi-word fillers count as 1 each).
     */
    fun count(text: String): Int {
        if (text.isBlank()) return 0

        // Normalise: lowercase, collapse whitespace, strip leading/trailing punctuation per word.
        val normalised = text.lowercase().replace(Regex("[^a-z\\s]"), " ")

        var multiWordCount = 0
        var remaining = normalised

        // Pass 1: count and erase multi-word fillers to avoid component word double-count.
        for (phrase in MULTI_WORD_FILLERS) {
            var idx = remaining.indexOf(phrase)
            while (idx >= 0) {
                multiWordCount++
                // Replace with spaces of equal length to preserve word boundaries.
                remaining = remaining.substring(0, idx) +
                    " ".repeat(phrase.length) +
                    remaining.substring(idx + phrase.length)
                idx = remaining.indexOf(phrase)
            }
        }

        // Pass 2: tokenise remaining text and count single-word fillers.
        val singleWordCount = remaining
            .split(Regex("\\s+"))
            .count { it.isNotBlank() && SINGLE_WORD_FILLERS.contains(it) }

        return multiWordCount + singleWordCount
    }
}
