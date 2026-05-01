package com.meetmind.assistant.domain.usecase.transcription

import com.meetmind.assistant.domain.audio.AudioStorage
import com.meetmind.assistant.domain.model.DiarizationResult
import com.meetmind.assistant.domain.model.DiarizationStatus
import com.meetmind.assistant.domain.model.SpeakerSpan
import com.meetmind.assistant.domain.model.TranscriptionSegment
import com.meetmind.assistant.domain.repository.DiarizationRepository
import com.meetmind.assistant.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.first

/**
 * Run end-of-session speaker diarization for a session.
 *
 * Pipeline:
 *   1. Load the session and verify it has a retained audio file and a status
 *      that allows running (NOT_RUN or FAILED — re-runs are allowed).
 *   2. Move the session to [DiarizationStatus.RUNNING] so the UI can show
 *      progress.
 *   3. Call [DiarizationRepository.diarize] which produces the raw speaker
 *      spans for the audio.
 *   4. Align spans to segments by audio offset (each segment's
 *      [TranscriptionSegment.startOffsetMs] / [TranscriptionSegment.endOffsetMs]
 *      gets the cluster of whichever span overlaps it most).
 *   5. Persist per-segment cluster assignments.
 *   6. Mark the session [DiarizationStatus.COMPLETED] and delete the WAV
 *      file via [AudioStorage] — diarization output is the canonical
 *      record now; the audio is no longer needed.
 *
 * Failures move the session to [DiarizationStatus.FAILED] and leave the
 * audio file intact so the user can retry.
 */
class RunDiarizationUseCase(
    private val transcriptionRepository: TranscriptionRepository,
    private val diarizationRepository: DiarizationRepository,
    private val audioStorage: AudioStorage
) {

    /**
     * @return The diarization result on success, or a failure with the
     *   underlying reason. The session entity has been updated either way:
     *   COMPLETED (with audio deleted) on success, FAILED (audio preserved)
     *   on failure.
     */
    suspend operator fun invoke(sessionId: String): Result<DiarizationResult> {
        val session = transcriptionRepository.getSession(sessionId).first()
            ?: return Result.failure(IllegalStateException("Session not found: $sessionId"))

        val audioPath = session.audioFilePath
            ?: return Result.failure(
                IllegalStateException(
                    "No audio file retained for this session. Diarization is only " +
                        "available on sessions recorded after the feature was enabled."
                )
            )

        if (session.diarizationStatus == DiarizationStatus.RUNNING) {
            return Result.failure(IllegalStateException("Diarization is already in progress for this session."))
        }

        // Mark RUNNING so any subscriber to the session Flow can show progress.
        transcriptionRepository.updateSessionDiarizationStatus(sessionId, DiarizationStatus.RUNNING)

        val result = diarizationRepository.diarize(audioPath)
        if (result.isFailure) {
            transcriptionRepository.updateSessionDiarizationStatus(sessionId, DiarizationStatus.FAILED)
            return result
        }
        val diarization = result.getOrThrow()

        // Align clusters with segments and persist.
        val segments = transcriptionRepository.getSegmentsBySessionOnce(sessionId)
        val assignments = alignSpansToSegments(segments, diarization.spans)
        for ((segmentId, cluster) in assignments) {
            transcriptionRepository.updateSegmentCluster(segmentId, cluster)
        }

        // Mark COMPLETED, drop the audio file path on the session, and delete the file.
        transcriptionRepository.updateSessionAudioFile(
            sessionId = sessionId,
            audioFilePath = null,
            diarizationStatus = DiarizationStatus.COMPLETED
        )
        audioStorage.deleteAudioForSession(sessionId)

        return Result.success(diarization)
    }

    /**
     * For each segment with valid offsets, pick the [SpeakerSpan] that
     * overlaps it the most (by milliseconds) and emit (segmentId, cluster).
     * Segments without offsets are silently skipped — they'll stay null and
     * fall through to the existing manual-label UX.
     *
     * Visible for testing.
     */
    internal fun alignSpansToSegments(
        segments: List<TranscriptionSegment>,
        spans: List<SpeakerSpan>
    ): List<Pair<String, Int>> {
        if (spans.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, Int>>(segments.size)
        for (segment in segments) {
            val s = segment.startOffsetMs ?: continue
            val e = segment.endOffsetMs ?: continue
            if (e <= s) continue
            // Linear scan: spans are time-ordered and segments are short
            // (~30 s max), so cost is bounded. If we ever need it, we can
            // switch to binary search by start time.
            var bestCluster = -1
            var bestOverlap = 0L
            for (span in spans) {
                val overlapStart = maxOf(s, span.startMs)
                val overlapEnd = minOf(e, span.endMs)
                val overlap = overlapEnd - overlapStart
                if (overlap > bestOverlap) {
                    bestOverlap = overlap
                    bestCluster = span.cluster
                }
            }
            if (bestCluster >= 0) out.add(segment.id to bestCluster)
        }
        return out
    }
}
