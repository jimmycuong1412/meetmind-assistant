package com.meetmind.assistant.domain.usecase.ask

import com.meetmind.assistant.domain.usecase.llm.InsightOutputParser

/**
 * A cleaned-up model answer.
 *
 * @property text Answer with citation markers renumbered `[1]`, `[2]`… in order of
 *   first appearance; markers pointing at excerpts that do not exist are removed.
 * @property citedExcerpts 0-based excerpt indexes, where `citedExcerpts[k]` is the
 *   excerpt behind marker `[k + 1]` in [text].
 * @property notFound True when the model said the meetings do not contain the answer.
 */
data class ParsedAskAnswer(
    val text: String,
    val citedExcerpts: List<Int>,
    val notFound: Boolean
)

/**
 * Turns raw model output into an answer the UI can render with citation chips.
 *
 * Small on-device models follow the citation instructions loosely, so this accepts
 * `[2]`, `[2, 3]`, `[2][3]`, `[Excerpt 2]` and a trailing "Sources:" list, and treats
 * `NOT_FOUND` anywhere at the start as the not-found signal.
 */
object AskAnswerParser {

    const val NOT_FOUND_TOKEN = "NOT_FOUND"

    private val MARKER = Regex(
        "\\[\\s*(?:excerpts?|sources?|đoạn)?\\s*(\\d+(?:\\s*[,;]\\s*\\d+)*)\\s*]",
        RegexOption.IGNORE_CASE
    )
    private val NOT_FOUND_PREFIX = Regex("^\\s*NOT[_ ]FOUND\\b[\\s:.,;\\-–—]*", RegexOption.IGNORE_CASE)
    private val ANSWER_PREFIX = Regex("^\\s*(answer|trả lời)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val SOURCES_SECTION = Regex(
        "(?im)^\\s*\\**\\s*(sources|references|citations|nguồn)\\s*\\**\\s*:.*",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parse(raw: String, excerptCount: Int): ParsedAskAnswer {
        var text = InsightOutputParser.stripCodeFences(raw.trim())
        text = ANSWER_PREFIX.replace(text, "")

        val notFound = NOT_FOUND_PREFIX.containsMatchIn(text)
        if (notFound) text = NOT_FOUND_PREFIX.replace(text, "")

        // Number citations by first appearance across the whole output, including a
        // trailing sources list, before that list is cut from the visible text.
        val displayNumber = linkedMapOf<Int, Int>()
        MARKER.findAll(text).forEach { match ->
            numbersIn(match).forEach { n ->
                if (n in 1..excerptCount && n !in displayNumber) displayNumber[n] = displayNumber.size + 1
            }
        }

        text = SOURCES_SECTION.replace(text, "").trimEnd()
        text = MARKER.replace(text) { match ->
            numbersIn(match)
                .mapNotNull { displayNumber[it] }
                .distinct()
                .joinToString("") { "[$it]" }
        }
        text = text
            .replace(Regex("[ \\t]+([.,;:!?])"), "$1")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()

        return ParsedAskAnswer(
            text = text,
            citedExcerpts = displayNumber.keys.map { it - 1 },
            notFound = notFound
        )
    }

    /** Text safe to show while tokens stream in: no raw markers, no not-found token. */
    fun streamingPreview(partial: String): String =
        MARKER.replace(NOT_FOUND_PREFIX.replace(ANSWER_PREFIX.replace(partial, ""), ""), "")
            .replace(Regex("\\[[^\\]\\n]{0,12}$"), "")
            .replace(Regex("[ \\t]+([.,;:!?])"), "$1")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trimStart()

    private fun numbersIn(match: MatchResult): List<Int> =
        match.groupValues[1].split(',', ';').mapNotNull { it.trim().toIntOrNull() }
}
