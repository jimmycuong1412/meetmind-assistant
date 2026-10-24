package com.meetmind.assistant.domain.usecase.coach

/**
 * How long the candidate has been answering one interview question.
 *
 * Times are positions on the **recording clock** (`recordingDurationMillis`), not wall
 * time, so a paused-and-resumed recording does not count the pause as answering.
 *
 * @property endedAtMs Set when the next question arrives or recording stops.
 */
data class AnswerTiming(
    val startedAtMs: Long,
    val endedAtMs: Long? = null
) {
    val isRunning: Boolean get() = endedAtMs == null

    fun elapsedMs(nowMs: Long): Long = ((endedAtMs ?: nowMs) - startedAtMs).coerceAtLeast(0)
}

/**
 * Answer-duration timers for question cards (spec 010, US4).
 *
 * Only one answer runs at a time: a new question ends the previous answer. The original
 * implementation ticked every card's timer until recording stopped, so earlier cards kept
 * counting while later questions were being answered.
 */
object AnswerTimeline {

    /** Below this, a finished answer gets the "consider elaborating" nudge. */
    const val SHORT_ANSWER_MS = 30_000L

    /** From here the card border turns to the warning color. */
    const val LONG_ANSWER_MS = 90_000L

    /** From here the card border turns to the error color. */
    const val TOO_LONG_ANSWER_MS = 120_000L

    enum class Pace { ON_TRACK, LONG, TOO_LONG }

    /** Start timing [questionId] at [atMs], ending whichever answer was running. */
    fun start(timings: Map<String, AnswerTiming>, questionId: String, atMs: Long): Map<String, AnswerTiming> =
        endAll(timings, atMs) + (questionId to AnswerTiming(startedAtMs = atMs))

    /** End every running answer at [atMs] — called when recording stops. */
    fun endAll(timings: Map<String, AnswerTiming>, atMs: Long): Map<String, AnswerTiming> =
        timings.mapValues { (_, timing) ->
            if (timing.isRunning) timing.copy(endedAtMs = maxOf(atMs, timing.startedAtMs)) else timing
        }

    fun pace(elapsedMs: Long): Pace = when {
        elapsedMs >= TOO_LONG_ANSWER_MS -> Pace.TOO_LONG
        elapsedMs >= LONG_ANSWER_MS -> Pace.LONG
        else -> Pace.ON_TRACK
    }

    /** True for a finished answer that was shorter than [SHORT_ANSWER_MS]. Never shown live. */
    fun isShortAnswer(timing: AnswerTiming): Boolean =
        timing.endedAtMs?.let { it - timing.startedAtMs < SHORT_ANSWER_MS } ?: false
}
