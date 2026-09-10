package com.meetmind.assistant.domain.usecase.sync

import com.meetmind.assistant.domain.model.SpeakerChannel

/**
 * What the detector wants the caller to do next.
 *
 * `null` from [InterviewTurnDetector.poll] / [InterviewTurnDetector.onSegment] means
 * "do nothing" — the common case on most segments and most ticks.
 */
sealed interface InterviewTrigger {

    /**
     * The interviewer finished asking something. Run inference now.
     *
     * @property text The interviewer's turn — every clause since they took the floor,
     *   not merely the final sentence, so multi-part questions and their lead-in
     *   context reach the model intact.
     */
    data class Question(val text: String) : InterviewTrigger

    /**
     * No question has been detected for a full interval. Run a general coaching pass so
     * the mode still produces something when question detection misses (unusual
     * phrasing, or attribution that came back `UNKNOWN`).
     */
    data object Heartbeat : InterviewTrigger

    /**
     * The candidate started speaking while generation was still running — abandon it.
     *
     * Deliberate product behaviour, not an optimization: an answer that lands after the
     * candidate has begun talking pulls their attention at the worst possible moment.
     * Late help is worse than no help.
     */
    data object Cancel : InterviewTrigger
}

/**
 * Decides *when* Interview Mode should ask the LLM for help.
 *
 * Replaces the fixed `interviewIntervalSeconds` poll, which could leave a candidate
 * waiting up to 30 s after a question — long past the point the silence turned
 * awkward, and sometimes landing mid-answer, which is actively harmful.
 *
 * ## Design
 *
 * A pure state machine: no coroutines, no clock of its own, no I/O. The caller supplies
 * `nowMs`, which makes every behaviour below exercisable in an instant unit test rather
 * than through real delays.
 *
 * ## Why a debounce
 *
 * Senior interviewers rarely ask atomic questions. *"How do you handle secrets? And what
 * about rotation?"* arrives as two segments a few hundred ms apart. Firing on the first
 * clause burns the inference and shows half an answer, so the detector waits
 * [TURN_BOUNDARY_DEBOUNCE_MS] of interviewer silence and restarts that window on every
 * new clause. Non-question lead-in is accumulated too — *"We run 200 services. How would
 * you scale that?"* is unanswerable if only the second sentence reaches the model.
 *
 * ## Latency budget
 * VAD finalize (~300 ms) + debounce (900 ms) + generation (~1.5–2.5 s) ≈ **3–4 s** after
 * the question ends, which lands inside a natural thinking pause.
 *
 * ## What it refuses to do
 * - Fire on [SpeakerChannel.CANDIDATE] speech — the failure that makes the assistant
 *   answer its own user.
 * - Fire on [SpeakerChannel.UNKNOWN]. In single-mic mode the attribution heuristic
 *   reports `UNKNOWN` when it is not confident, and acting on that would answer the
 *   candidate's own words a large fraction of the time.
 * - Start anything while generation is in flight.
 *
 * Not thread-safe; call from a single coroutine (the STT collect loop), as
 * [SyncSttLlmUseCase] does.
 */
class InterviewTurnDetector(
    /**
     * Accept questions from [SpeakerChannel.UNKNOWN] segments.
     *
     * Set when no capture producer can separate the speakers — today that is *always*,
     * because the multi-source capture path (plan Tasks 3/3b) has not shipped and every
     * segment therefore arrives `UNKNOWN`. With this false, the detector would refuse
     * every segment and Interview Mode would degrade to heartbeat-only: strictly worse
     * than the 30 s poll this replaces.
     *
     * The trade is explicit. Without attribution, the question test carries the whole
     * decision, so the candidate's own clarifiers ("Do you mean at the cluster level?")
     * can trigger a spurious answer. That is an acceptable cost while it is the only way
     * the mode works at all, and it disappears once a real producer lands — known
     * attribution always wins, so a CANDIDATE-attributed question never fires even here.
     */
    private val allowUnattributedQuestions: Boolean = false
) {

    companion object {
        /**
         * Interviewer silence required before a turn is treated as finished.
         *
         * 900 ms is chosen to sit above a natural inter-clause pause (~300–600 ms) but
         * below the point a candidate would start answering, so multi-part questions
         * coalesce without the help arriving late.
         */
        const val TURN_BOUNDARY_DEBOUNCE_MS = 900L

        /**
         * Interviewer silence after which the accumulated turn is abandoned rather than
         * fired. Guards against a stale fragment from minutes ago being sent as though
         * it were the current question — e.g. when a question was detected but
         * generation was busy and the moment has since passed.
         */
        const val TURN_STALE_AFTER_MS = 15_000L

        /** Interrogative openers. Mirrors SpeakerAttributionHeuristic's list. */
        private val INTERROGATIVE_OPENERS = listOf(
            "what", "how", "why", "when", "where", "which", "who",
            "can you", "could you", "would you", "do you", "did you",
            "have you", "are you", "is there", "was there"
        )

        /**
         * Questions phrased as commands. Senior interviews lean on these heavily and
         * they carry no question mark, so a naive `endsWith("?")` test misses exactly
         * the questions that matter most.
         */
        private val IMPERATIVE_PROBES = listOf(
            "walk me through", "tell me about", "tell me how", "describe",
            "explain", "give me an example", "talk me through", "let's say",
            "suppose", "imagine", "how would you", "what would you"
        )

        /** True when [text] reads as a question or an imperative probe. */
        fun looksLikeQuestion(text: String): Boolean {
            val t = text.trim()
            if (t.isEmpty()) return false
            val lower = t.lowercase()
            return t.contains("?") ||
                INTERROGATIVE_OPENERS.any { lower.startsWith(it) } ||
                IMPERATIVE_PROBES.any { lower.startsWith(it) }
        }
    }

    /** Clauses accumulated for the interviewer's current turn, in order. */
    private val pendingTurn = mutableListOf<String>()

    /** True when at least one accumulated clause reads as a question. */
    private var pendingHasQuestion = false

    /** Timestamp of the most recent interviewer clause; drives the debounce. */
    private var lastInterviewerMs: Long = 0L

    /** Timestamp of the last fired trigger; drives the heartbeat window. */
    private var lastTriggerMs: Long = 0L

    /** True between [onGenerationStarted] and [onGenerationFinished]. */
    private var isGenerating = false

    /** Ensures one [InterviewTrigger.Cancel] per generation, not one per segment. */
    private var cancelEmitted = false

    /**
     * Feed a finalized transcript segment.
     *
     * @param channel Who spoke. Only [SpeakerChannel.INTERVIEWER] can start a question;
     *   [SpeakerChannel.CANDIDATE] can trigger barge-in cancellation.
     * @param nowMs Monotonic-ish timestamp for this segment.
     * @param heartbeatIntervalMs Fallback interval, from `interviewIntervalSeconds`.
     * @return A trigger to act on immediately, or null.
     */
    fun onSegment(
        text: String,
        channel: SpeakerChannel,
        nowMs: Long,
        heartbeatIntervalMs: Long
    ): InterviewTrigger? {
        // Seed the heartbeat window on the first segment so it measures from the start
        // of speech rather than from epoch zero.
        if (lastTriggerMs == 0L) lastTriggerMs = nowMs

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        when (channel) {
            SpeakerChannel.INTERVIEWER -> {
                // Drop a turn that has gone stale — see TURN_STALE_AFTER_MS.
                if (pendingTurn.isNotEmpty() && nowMs - lastInterviewerMs > TURN_STALE_AFTER_MS) {
                    resetTurn()
                }
                pendingTurn += trimmed
                if (looksLikeQuestion(trimmed)) pendingHasQuestion = true
                lastInterviewerMs = nowMs
                // Never fire here: the interviewer may still be mid-turn. poll() decides
                // once the debounce has elapsed.
                return null
            }

            SpeakerChannel.CANDIDATE -> {
                // The candidate taking the floor ends any interviewer turn we were
                // accumulating — if it did not fire before they started answering, it is
                // no longer useful.
                resetTurn()

                if (isGenerating && !cancelEmitted) {
                    cancelEmitted = true
                    return InterviewTrigger.Cancel
                }
                return null
            }

            SpeakerChannel.UNKNOWN -> {
                // No confident attribution. With a real producer available we contribute
                // nothing rather than risk answering the candidate's own words. In
                // single-mic mode that would silence the feature entirely, so the
                // question test stands in for attribution — see the constructor doc.
                if (!allowUnattributedQuestions) return null

                if (pendingTurn.isNotEmpty() && nowMs - lastInterviewerMs > TURN_STALE_AFTER_MS) {
                    resetTurn()
                }
                // Only question-shaped text is accumulated here. Unlike the INTERVIEWER
                // branch we cannot bank lead-in context, because an unattributed
                // statement is as likely to be the candidate answering as the
                // interviewer setting up.
                if (looksLikeQuestion(trimmed)) {
                    pendingTurn += trimmed
                    pendingHasQuestion = true
                    lastInterviewerMs = nowMs
                }
                return null
            }
        }
    }

    /**
     * Advance time without a new segment. Call on each STT tick.
     *
     * This is where questions actually fire: [onSegment] only accumulates, because at
     * the moment a clause arrives we cannot yet know whether the interviewer is done.
     *
     * @return A trigger to act on, or null.
     */
    fun poll(nowMs: Long, heartbeatIntervalMs: Long): InterviewTrigger? {
        if (lastTriggerMs == 0L) lastTriggerMs = nowMs

        // Never stack inference on in-flight inference.
        if (isGenerating) return null

        // 1. A completed interviewer turn containing a question.
        if (pendingHasQuestion && pendingTurn.isNotEmpty()) {
            val silence = nowMs - lastInterviewerMs
            if (silence >= TURN_BOUNDARY_DEBOUNCE_MS) {
                if (silence > TURN_STALE_AFTER_MS) {
                    // Too old to be the current question — drop rather than mislead.
                    resetTurn()
                } else {
                    val text = pendingTurn.joinToString(" ")
                    resetTurn()
                    lastTriggerMs = nowMs
                    return InterviewTrigger.Question(text)
                }
            }
        }

        // 2. Fallback heartbeat, so interviewIntervalSeconds keeps its meaning and a
        //    missed question does not mean silence for the rest of the interview.
        if (nowMs - lastTriggerMs >= heartbeatIntervalMs) {
            lastTriggerMs = nowMs
            return InterviewTrigger.Heartbeat
        }

        return null
    }

    /** Mark inference as started; suppresses new triggers and arms barge-in. */
    fun onGenerationStarted() {
        isGenerating = true
        cancelEmitted = false
    }

    /** Mark inference as finished (success, failure, or cancellation). */
    fun onGenerationFinished() {
        isGenerating = false
        cancelEmitted = false
    }

    private fun resetTurn() {
        pendingTurn.clear()
        pendingHasQuestion = false
    }
}
