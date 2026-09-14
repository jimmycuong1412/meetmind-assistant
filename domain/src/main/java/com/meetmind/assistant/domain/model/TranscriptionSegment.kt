package com.meetmind.assistant.domain.model

/**
 * Represents a single segment of transcribed text from speech-to-text.
 *
 * Each segment belongs to a specific transcription session, allowing multiple
 * independent recording sessions to be stored separately.
 *
 * @property id Unique identifier for this segment
 * @property sessionId ID of the session this segment belongs to
 * @property text The transcribed text content
 * @property timestamp Unix timestamp (milliseconds) when this segment was created
 * @property isComplete Whether this segment represents a complete utterance (speech ended)
 * @property speaker Optional manual speaker label (e.g. "Me", "Person A"). Wins over
 *   [speakerCluster] for display, and is propagated to all segments sharing the same
 *   [speakerCluster] via cluster-aware label propagation.
 * @property speakerCluster Diarization cluster id (0..N-1) assigned by the offline
 *   diarization pipeline. Null until diarization has run on the parent session, or for
 *   segments whose audio range failed to be diarized.
 * @property startOffsetMs Audio offset of segment start, measured from the beginning of
 *   the session's recording in milliseconds. Used to align this segment with diarization
 *   speaker spans. Null for segments produced before audio offsets were tracked.
 * @property endOffsetMs Audio offset of segment end, in milliseconds since the start of
 *   the session's recording. Null for partials and for legacy segments.
 * @property speakerChannel Which party spoke this segment, resolved **live** from the
 *   capture source (Interview Mode). Distinct from [speakerCluster], which is an
 *   acoustic guess produced after the fact by the offline diarization pipeline: a
 *   channel is known at capture time, typically with certainty.
 *
 *   Null when live attribution does not apply (non-interview modes, or segments
 *   recorded before this existed). [com.meetmind.assistant.domain.model.SpeakerChannel.UNKNOWN]
 *   is different from null — it means attribution was attempted and was not confident.
 *
 *   Not yet persisted: the Room column arrives with the capture producer (plan Task 3),
 *   since nothing writes a non-null value until then.
 */
data class TranscriptionSegment(
    val id: String,
    val sessionId: String,
    val text: String,
    val timestamp: Long,
    val isComplete: Boolean,
    val speaker: String? = null,
    val speakerCluster: Int? = null,
    val speakerChannel: SpeakerChannel? = null,
    val startOffsetMs: Long? = null,
    val endOffsetMs: Long? = null
)
