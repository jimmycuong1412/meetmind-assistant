package com.meetmind.assistant.domain.usecase.transcription

import com.meetmind.assistant.domain.model.SpeakerChannel

/**
 * Linguistic fallback for guessing who spoke, used **only** when true channel
 * separation is unavailable.
 *
 * Interview Mode's primary path derives speaker identity from the capture source
 * (microphone ⇒ candidate, playback loopback ⇒ interviewer), which is certain. This
 * heuristic exists for the cases where that is impossible:
 *
 *  - the user declined the screen/audio-capture consent prompt;
 *  - the conferencing app opted out of playback capture, or routes call audio as
 *    `USAGE_VOICE_COMMUNICATION`, which the OS does not allow capturing;
 *  - an in-person interview, where one microphone hears both people.
 *
 * ## Governing principle: prefer honest unknowns
 *
 * These labels are fed into the LLM prompt as `INTERVIEWER:` / `CANDIDATE:` prefixes.
 * A **confidently wrong** label is therefore worse than no label at all: it makes the
 * model answer the candidate's own sentences, or coach the interviewer's speech. So
 * every rule below is tuned to abstain rather than guess, and anything scoring below
 * [CONFIDENCE_FLOOR] is reported as [SpeakerChannel.UNKNOWN].
 *
 * ## Signals used
 *
 * | Signal | Reads as |
 * |---|---|
 * | Interrogative form, after a turn-length pause | interviewer |
 * | Imperative probe ("walk me through…") — carries no `?` | interviewer |
 * | Long utterance following an interviewer turn | candidate |
 * | Continuation after a short pause | same speaker as before |
 * | Short//ambiguous utterance | unknown |
 *
 * The heuristic is stateless: the caller supplies the previous attribution and the
 * silence that preceded this utterance, which keeps it a pure function and trivially
 * testable.
 */
class SpeakerAttributionHeuristic {

    companion object {
        /**
         * Minimum confidence required before a caller may present an attribution as
         * fact. Below this, render "Speaker ?" and omit the speaker prefix from the
         * LLM prompt. Exported so call sites reference the constant rather than
         * duplicating a magic number.
         */
        const val CONFIDENCE_FLOOR = 0.65f

        /**
         * Silence long enough to read as a change of turn. Below this, an utterance
         * is more likely a continuation of the same speaker (a thinking pause).
         */
        private const val TURN_GAP_MS = 1_500L

        /** Word count above which an utterance reads as a substantive answer. */
        private const val LONG_UTTERANCE_WORDS = 25

        /**
         * Utterances at or below this many words carry too little signal to attribute
         * ("Right.", "Mm-hm.", "Sure."). Always [SpeakerChannel.UNKNOWN].
         */
        private const val MIN_WORDS_FOR_ATTRIBUTION = 3

        /**
         * Openers for questions phrased as commands. Senior interviews lean on these
         * heavily and they carry no question mark, so a naive `endsWith("?")` test
         * misses precisely the questions that matter most.
         */
        private val IMPERATIVE_PROBES = listOf(
            "walk me through", "tell me about", "tell me how", "describe",
            "explain", "give me an example", "talk me through", "let's say",
            "suppose", "imagine", "how would you", "what would you"
        )

        /** Leading words that mark an interrogative clause. */
        private val INTERROGATIVE_OPENERS = listOf(
            "what", "how", "why", "when", "where", "which", "who",
            "can you", "could you", "would you", "do you", "did you",
            "have you", "are you", "is there", "was there"
        )
    }

    /**
     * One attribution decision.
     *
     * @property channel Best guess, or [SpeakerChannel.UNKNOWN] when not confident.
     * @property confidence In `[0f, 1f]`. Compare against [CONFIDENCE_FLOOR] before
     *   presenting [channel] as fact.
     */
    data class Attribution(
        val channel: SpeakerChannel,
        val confidence: Float
    )

    /**
     * Attribute a single completed utterance.
     *
     * @param text The finalized transcript of the utterance.
     * @param silenceBeforeMs Gap between the end of the previous utterance and the
     *   start of this one. Drives turn-change detection.
     * @param previous Attribution of the previous utterance, or null at session start.
     */
    fun attribute(
        text: String,
        silenceBeforeMs: Long,
        previous: SpeakerChannel?
    ): Attribution {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Attribution(SpeakerChannel.UNKNOWN, 0f)

        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val normalized = trimmed.lowercase()
        val isTurnChange = silenceBeforeMs >= TURN_GAP_MS

        // Too short to carry signal. Abstain regardless of anything else — these are
        // the back-channel noises ("Right.", "Got it.") that both parties make.
        if (words.size < MIN_WORDS_FOR_ATTRIBUTION) {
            return Attribution(SpeakerChannel.UNKNOWN, 0.2f)
        }

        val looksLikeQuestion = trimmed.endsWith("?") ||
            INTERROGATIVE_OPENERS.any { normalized.startsWith(it) }
        val looksLikeProbe = IMPERATIVE_PROBES.any { normalized.startsWith(it) }

        // ── Interviewer signals ──────────────────────────────────────────────────
        if (looksLikeQuestion || looksLikeProbe) {
            return when {
                // A question opening a fresh turn is the strongest signal available.
                isTurnChange -> Attribution(SpeakerChannel.INTERVIEWER, 0.9f)

                // A question with no preceding pause, while the candidate holds the
                // floor, is most likely the candidate's own clarifier ("Do you mean
                // at the cluster level?") or a rhetorical aside inside their answer.
                // Attributing this to the interviewer is the single most damaging
                // error this heuristic can make: it makes the assistant answer the
                // candidate's own sentence. Abstain.
                previous == SpeakerChannel.CANDIDATE ->
                    Attribution(SpeakerChannel.UNKNOWN, 0.4f)

                else -> Attribution(SpeakerChannel.INTERVIEWER, 0.7f)
            }
        }

        // ── Candidate signals ────────────────────────────────────────────────────
        // A substantive utterance right after the interviewer spoke is the answer.
        if (words.size > LONG_UTTERANCE_WORDS && previous == SpeakerChannel.INTERVIEWER) {
            return Attribution(SpeakerChannel.CANDIDATE, 0.85f)
        }

        // Continuation: no turn-length pause means the floor did not change hands.
        if (!isTurnChange && previous != null && previous != SpeakerChannel.UNKNOWN) {
            return Attribution(previous, 0.75f)
        }

        // A long non-question utterance is far more likely the candidate answering
        // than the interviewer monologuing, but without a prior turn to anchor it
        // this stays below the floor.
        if (words.size > LONG_UTTERANCE_WORDS) {
            return Attribution(SpeakerChannel.CANDIDATE, 0.6f)
        }

        return Attribution(SpeakerChannel.UNKNOWN, 0.3f)
    }
}
