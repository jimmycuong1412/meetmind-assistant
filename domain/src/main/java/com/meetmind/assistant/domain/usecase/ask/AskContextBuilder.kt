package com.meetmind.assistant.domain.usecase.ask

import com.meetmind.assistant.domain.model.SessionWithDetails
import kotlin.math.ln

/**
 * A piece of meeting text the model may cite: a run of consecutive transcript
 * segments from one speaker, or one AI insight (summary) of a meeting.
 *
 * @property sessionIndex Position of the meeting in the chat's selection; keeps
 *   excerpts grouped by meeting in the prompt.
 * @property order Position within the meeting (insights use -1 so summaries lead).
 */
data class AskPassage(
    val sessionIndex: Int,
    val sessionId: String,
    val sessionName: String?,
    val segmentId: String?,
    val offsetMs: Long?,
    val speaker: String?,
    val text: String,
    val isInsight: Boolean,
    val order: Int
)

/**
 * The excerpts chosen for one question, numbered by their position in [passages]
 * (excerpt `[1]` is `passages[0]`).
 *
 * @property truncated True when some meeting text did not fit the budget.
 */
data class AskContext(
    val passages: List<AskPassage>,
    val truncated: Boolean
)

/**
 * Picks the meeting text that fits in one on-device prompt.
 *
 * The model's context window is 4 096 tokens, shared by the system prompt, the
 * excerpts, the question and the answer, so a long selection cannot be sent whole.
 * Passages are scored against the question with a small keyword model (term
 * frequency × inverse document frequency over the selected meetings); the best ones
 * are kept until [DEFAULT_BUDGET_CHARS] is reached. Questions that match nothing —
 * "summarise these meetings" — fall back to each meeting's AI insights plus passages
 * spread evenly through every meeting.
 *
 * Pure Kotlin with no LLM call, so retrieval is unit-tested on its own.
 */
object AskContextBuilder {

    /** ~1 700 tokens at ~4.1 chars/token: leaves room for prompt, history and answer. */
    const val DEFAULT_BUDGET_CHARS = 7_000

    private const val PASSAGE_TARGET_CHARS = 420
    private const val INSIGHT_MAX_CHARS = 600

    /** Rough per-excerpt cost of the `[n] "Meeting" · 12:04 · Speaker:` header. */
    private const val HEADER_CHARS = 48

    /** Terms longer than this are compared by prefix, a cheap stand-in for stemming. */
    private const val STEM_LENGTH = 5

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    private val STOPWORDS = setOf(
        // English
        "the", "and", "for", "are", "was", "were", "what", "which", "who", "whom", "when",
        "where", "why", "how", "did", "does", "do", "is", "it", "its", "of", "to", "in", "on",
        "at", "by", "an", "or", "be", "been", "this", "that", "these", "those", "we", "us",
        "our", "you", "your", "they", "them", "their", "he", "she", "his", "her", "about",
        "with", "from", "any", "all", "there", "have", "has", "had", "can", "could", "would",
        "should", "will", "me", "my", "i", "a", "so", "as", "if", "not", "no", "yes", "into",
        "meeting", "meetings", "say", "said", "tell", "please", "list", "give",
        // Vietnamese (syllables)
        "là", "của", "và", "có", "không", "gì", "nào", "được", "cho", "trong", "với", "này",
        "đó", "những", "các", "một", "về", "đã", "sẽ", "thì", "mà", "ai", "ở", "khi", "nhưng",
        "cuộc", "họp", "chúng", "ta", "tôi", "bạn", "nói", "hãy", "liệt", "kê"
    )

    /** Score, pick and order the excerpts for [question]. */
    fun build(
        sessions: List<SessionWithDetails>,
        question: String,
        budgetChars: Int = DEFAULT_BUDGET_CHARS
    ): AskContext = select(buildPassages(sessions), question, budgetChars)

    /** Split every meeting into citable passages. */
    fun buildPassages(sessions: List<SessionWithDetails>): List<AskPassage> {
        val passages = mutableListOf<AskPassage>()
        sessions.forEachIndexed { sessionIndex, details ->
            val session = details.session

            details.insights
                .filter { it.content.isNotBlank() }
                .forEach { insight ->
                    val text = listOfNotNull(insight.title?.takeIf { it.isNotBlank() }, insight.content.trim())
                        .joinToString(": ")
                    passages += AskPassage(
                        sessionIndex = sessionIndex,
                        sessionId = session.id,
                        sessionName = session.name,
                        segmentId = null,
                        offsetMs = null,
                        speaker = null,
                        text = text.take(INSIGHT_MAX_CHARS),
                        isInsight = true,
                        order = -1
                    )
                }

            var order = 0
            var run = mutableListOf<com.meetmind.assistant.domain.model.TranscriptionSegment>()
            var runChars = 0
            fun flush() {
                if (run.isEmpty()) return
                val first = run.first()
                passages += AskPassage(
                    sessionIndex = sessionIndex,
                    sessionId = session.id,
                    sessionName = session.name,
                    segmentId = first.id,
                    offsetMs = first.startOffsetMs ?: (first.timestamp - session.createdAt).coerceAtLeast(0),
                    speaker = first.speaker,
                    text = run.joinToString(" ") { it.text.trim() },
                    isInsight = false,
                    order = order++
                )
                run = mutableListOf()
                runChars = 0
            }
            details.segments
                .filter { it.isComplete && it.text.isNotBlank() }
                .sortedBy { it.timestamp }
                .forEach { segment ->
                    val length = segment.text.trim().length + 1
                    val speakerChanged = run.isNotEmpty() && run.last().speaker != segment.speaker
                    if (run.isNotEmpty() && (speakerChanged || runChars + length > PASSAGE_TARGET_CHARS)) flush()
                    run += segment
                    runChars += length
                }
            flush()
        }
        return passages
    }

    /** Pick the passages for [question] within [budgetChars] and number them. */
    fun select(passages: List<AskPassage>, question: String, budgetChars: Int = DEFAULT_BUDGET_CHARS): AskContext {
        if (passages.isEmpty()) return AskContext(emptyList(), truncated = false)

        val queryKeys = keys(question).toSet()
        val passageKeys = passages.map { keys(it.text) }
        val documentFrequency = queryKeys.associateWith { key -> passageKeys.count { key in it } }
        val scores = passageKeys.map { tokens ->
            queryKeys.sumOf { key ->
                val tf = tokens.count { it == key }
                if (tf == 0) 0.0
                else (1 + ln(tf.toDouble())) * ln(1.0 + passages.size.toDouble() / documentFrequency.getValue(key))
            }
        }

        val ranked = passages.indices.filter { scores[it] > 0.0 }
            .sortedWith(compareByDescending<Int> { scores[it] }.thenBy { passages[it].order })
        // Matching passages first; then the meetings' AI summaries, which are compact and
        // help with "why"/"overall" questions; then whatever budget is left goes to
        // passages spread evenly through every meeting. A selection that fits the
        // budget is therefore sent whole, and only long ones are cut down.
        val insights = passages.indices.filter { passages[it].isInsight }
        val candidates = ranked + insights + spreadAcrossSessions(passages)

        val chosen = mutableListOf<Int>()
        var used = 0
        for (index in candidates.distinct()) {
            val cost = passages[index].text.length + HEADER_CHARS
            if (used + cost > budgetChars) {
                if (chosen.isEmpty()) {
                    chosen += index
                    used = budgetChars
                }
                continue
            }
            chosen += index
            used += cost
        }

        val ordered = chosen
            .map { index ->
                val passage = passages[index]
                if (passage.text.length + HEADER_CHARS > budgetChars) {
                    passage.copy(text = passage.text.take((budgetChars - HEADER_CHARS).coerceAtLeast(1)))
                } else {
                    passage
                }
            }
            .sortedWith(compareBy<AskPassage> { it.sessionIndex }.thenBy { it.order })
        return AskContext(passages = ordered, truncated = ordered.size < passages.size)
    }

    /**
     * Render the numbered excerpts for the prompt. [meetingLabel] names a meeting
     * whose session has no user-given name (the caller localizes it).
     */
    fun formatExcerpts(context: AskContext, meetingLabel: (sessionIndex: Int) -> String): String =
        context.passages.mapIndexed { i, passage ->
            val name = passage.sessionName?.takeIf { it.isNotBlank() } ?: meetingLabel(passage.sessionIndex)
            val header = buildList {
                add("\"$name\"")
                if (passage.isInsight) add("AI summary")
                passage.offsetMs?.let { add(formatOffset(it)) }
                passage.speaker?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            "[${i + 1}] $header: ${passage.text}"
        }.joinToString("\n")

    /** `m:ss` under an hour, `h:mm:ss` above. */
    fun formatOffset(offsetMs: Long): String {
        val totalSeconds = offsetMs.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    private fun keys(text: String): List<String> =
        WORD.findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= 2 && it !in STOPWORDS }
            .map { if (it.length > STEM_LENGTH) it.take(STEM_LENGTH) else it }
            .toList()

    /**
     * Transcript passages ordered so any prefix samples every meeting from start to
     * end: meetings take turns, and within one the order is middle-out by halves
     * (0, n/2, n/4, 3n/4, …).
     */
    private fun spreadAcrossSessions(passages: List<AskPassage>): List<Int> {
        val perSession = passages.indices
            .filter { !passages[it].isInsight }
            .groupBy { passages[it].sessionIndex }
            .toSortedMap()
            .values
            .map { indexes -> spreadOrder(indexes.size).map { indexes[it] } }
        val result = mutableListOf<Int>()
        val longest = perSession.maxOfOrNull { it.size } ?: 0
        for (round in 0 until longest) {
            perSession.forEach { order -> order.getOrNull(round)?.let { result += it } }
        }
        return result
    }

    private fun spreadOrder(n: Int): List<Int> {
        if (n <= 0) return emptyList()
        val order = mutableListOf(0)
        val seen = BooleanArray(n).also { it[0] = true }
        val queue = ArrayDeque(listOf(0 to n))
        while (queue.isNotEmpty()) {
            val (start, end) = queue.removeFirst()
            if (end - start < 2) continue
            val mid = (start + end) / 2
            if (!seen[mid]) {
                seen[mid] = true
                order += mid
            }
            queue.addLast(start to mid)
            queue.addLast(mid to end)
        }
        (0 until n).filterTo(order) { !seen[it] }
        return order
    }
}
