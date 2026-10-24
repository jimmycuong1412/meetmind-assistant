package com.meetmind.assistant.domain.usecase.coach

/**
 * Counts filler words in transcribed speech for Interview Mode coaching.
 *
 * Plain string matching — no model call — so it runs on every completed segment at no
 * inference cost. Rebuilt from spec 010 (US3) with two fixes over the original:
 *
 *  - Phrases are matched on word tokens, so "you know" no longer matches inside
 *    "you knowledge".
 *  - "like" and "so" were dropped as bare words: "I like Kotlin" and "so that" are not
 *    fillers, and counting them inflated the rate for everyone. "like" still counts when
 *    it is set off by commas or dashes (", like,"), the usual way STT punctuates it.
 *
 * Only call this on completed segments: partials are rewritten as speech continues.
 */
object FillerWordCounter {

    private val SINGLE_WORD_FILLERS = setOf(
        "um", "umm", "uh", "uhh", "uhm", "er", "erm", "hmm", "mm",
        "basically", "literally",
        // Vietnamese hesitation sounds
        "ờ", "ơ", "ừm"
    )

    private val PHRASE_FILLERS = listOf(
        listOf("you", "know"),
        listOf("i", "mean")
    )

    private val WORD = Regex("[\\p{L}']+")

    /** "like" between commas/dashes, or opening a clause before a comma. */
    private val PARENTHETICAL_LIKE = Regex("(^|[,;—–-])\\s*like\\s*(?=[,;—–-])")

    /** Number of filler instances in [text]; a phrase such as "you know" counts once. */
    fun count(text: String): Int {
        if (text.isBlank()) return 0
        val lower = text.lowercase()
        val tokens = WORD.findAll(lower).map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()

        var count = 0
        var i = 0
        while (i < tokens.size) {
            val phrase = PHRASE_FILLERS.firstOrNull { phrase ->
                i + phrase.size <= tokens.size && phrase.indices.all { tokens[i + it] == phrase[it] }
            }
            if (phrase != null) {
                count++
                i += phrase.size
                continue
            }
            if (tokens[i] in SINGLE_WORD_FILLERS) count++
            i++
        }
        return count + PARENTHETICAL_LIKE.findAll(lower).count()
    }
}
