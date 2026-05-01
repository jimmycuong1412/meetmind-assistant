package com.meetmind.assistant.domain.model

/**
 * Output of running speaker diarization on a recorded audio file.
 *
 * @property spans Time-ordered speaker spans covering the audio. Adjacent
 *   spans may share a [SpeakerSpan.cluster] when the same speaker continues
 *   across a short pause; consumers should treat clusters, not span
 *   boundaries, as the speaker identity.
 * @property clusterCount Total number of distinct speaker clusters detected.
 */
data class DiarizationResult(
    val spans: List<SpeakerSpan>,
    val clusterCount: Int
)

/**
 * One contiguous speaker span produced by diarization.
 *
 * @property startMs Span start in milliseconds since the recording started.
 * @property endMs Span end in milliseconds since the recording started.
 * @property cluster Stable id (0..N-1) identifying the speaker cluster this
 *   span belongs to. Spans with the same [cluster] value are spoken by the
 *   same person.
 */
data class SpeakerSpan(
    val startMs: Long,
    val endMs: Long,
    val cluster: Int
)

/**
 * Lifecycle states emitted while a diarization run is in progress. Drives the
 * "Run diarization" button + progress UI in session details.
 */
sealed interface DiarizationProgress {
    /** Pipeline has not been started for this session yet. */
    data object Idle : DiarizationProgress

    /**
     * Pipeline is loading audio and running segmentation. [fraction] is in
     * [0f, 1f] when the underlying engine reports progress, or null for
     * indeterminate.
     */
    data class Running(val fraction: Float?) : DiarizationProgress

    /** Pipeline completed successfully and clusters have been persisted. */
    data class Completed(val clusterCount: Int) : DiarizationProgress

    /** Pipeline failed; [reason] is a user-facing message. Audio retained. */
    data class Failed(val reason: String) : DiarizationProgress
}
