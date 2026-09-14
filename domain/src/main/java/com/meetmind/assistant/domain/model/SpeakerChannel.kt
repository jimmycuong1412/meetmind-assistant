package com.meetmind.assistant.domain.model

/**
 * Which party a piece of transcribed speech came from, in a two-party conversation
 * (currently: Interview Mode).
 *
 * ## Why this is a *channel*, not a cluster
 *
 * This is deliberately distinct from [TranscriptionSegment.speakerCluster], which is
 * produced by the **offline, post-session** diarization pipeline
 * ([com.meetmind.assistant.domain.usecase.transcription.RunDiarizationUseCase]) by
 * clustering voice embeddings. A cluster id is an *acoustic* guess made after the fact.
 *
 * A [SpeakerChannel] is instead resolved **live**, and — in the primary path —
 * with certainty rather than inference: when the far end of a video call is captured
 * separately from the microphone, speaker identity is simply a property of *which
 * capture source the audio arrived on*. No model, no clustering, no error.
 *
 * The enum is an abstraction over *source*, not over acoustics, which is what lets
 * the consumers stay stable if a future acoustic-diarization producer is added for
 * in-person interviews: it would populate the same values.
 *
 * ## Producers
 * 1. **Dual-source capture** (primary) — mic ⇒ [CANDIDATE], playback loopback ⇒
 *    [INTERVIEWER]. Certain.
 * 2. **[com.meetmind.assistant.domain.usecase.transcription.SpeakerAttributionHeuristic]**
 *    (fallback) — linguistic guess when only one source exists. Emits [UNKNOWN]
 *    whenever it is not confident.
 */
enum class SpeakerChannel {
    /** The person using the app — the interviewee. Captured from the microphone. */
    CANDIDATE,

    /** The far end of the call — the interviewer. Captured from playback loopback. */
    INTERVIEWER,

    /**
     * Attribution is not known.
     *
     * This is a **first-class, expected value**, not an error. It is emitted whenever
     * the heuristic fallback is below its confidence floor. Consumers must render it
     * honestly (e.g. "Speaker ?") and must not coerce it to a default party: the LLM
     * prompt is built from these labels, so a confident wrong label actively degrades
     * the generated answer, while an honest unknown merely withholds a hint.
     */
    UNKNOWN
}
