package com.meetmind.assistant.domain.usecase.transcription

import com.meetmind.assistant.domain.audio.AudioStorage
import com.meetmind.assistant.domain.model.DiarizationResult
import com.meetmind.assistant.domain.model.DiarizationStatus
import com.meetmind.assistant.domain.model.SpeakerSpan
import com.meetmind.assistant.domain.model.TranscriptionSegment
import com.meetmind.assistant.domain.notification.DiarizationNotifier
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
    private val audioStorage: AudioStorage,
    private val notifier: DiarizationNotifier? = null
) {

    /**
     * @return The diarization result on success, or a failure with the
     *   underlying reason. The session entity has been updated either way:
     *   COMPLETED (with audio deleted) on success, FAILED (audio preserved)
     *   on failure.
     */
    /**
     * @param clusterLabelProvider Receives a 1-indexed speaker number (1, 2, 3, …)
     *   for each unique cluster found in the diarization result and returns the
     *   localized default label to apply (e.g. "Speaker 1"). The label is only
     *   applied to segments that don't already have a manually-assigned speaker,
     *   so re-runs never overwrite user tags. Defaults to a non-localized
     *   "Speaker N" string for tests and callers that don't have a Context.
     */
    suspend operator fun invoke(
        sessionId: String,
        clusterLabelProvider: (Int) -> String = { n -> "Speaker $n" }
    ): Result<DiarizationResult> {
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
            // Notify the user even when they backgrounded the app — the run they
            // kicked off didn't finish and they probably want to know so they can retry.
            notifier?.notifyDiarizationFailed(
                sessionId = sessionId,
                sessionName = session.name,
                errorMessage = result.exceptionOrNull()?.message
            )
            return result
        }
        val diarization = result.getOrThrow()

        // Align clusters with segments and persist.
        val segments = transcriptionRepository.getSegmentsBySessionOnce(sessionId)
        val assignments = alignSpansToSegments(segments, diarization.spans)
        for ((segmentId, cluster) in assignments) {
            transcriptionRepository.updateSegmentCluster(segmentId, cluster)
        }

        // Auto-apply default labels ("Speaker 1", "Speaker 2", …) to clusters
        // so the transcript is immediately readable without forcing the user
        // to manually tag every speaker. Existing manual labels are preserved
        // (the DAO clause filters on `speaker IS NULL`), so re-running
        // diarization on a session you've already partially tagged won't
        // clobber your work. Cluster ids are renumbered to be 1-based and
        // contiguous in the order they first appear, so users always see
        // "Speaker 1, Speaker 2, …" regardless of the model's internal numbering.
        if (assignments.isNotEmpty()) {
            val clusterOrder = LinkedHashMap<Int, Int>()
            for ((_, cluster) in assignments) {
                if (cluster !in clusterOrder) clusterOrder[cluster] = clusterOrder.size + 1
            }
            val labels = clusterOrder.mapValues { (_, oneBasedIndex) ->
                clusterLabelProvider(oneBasedIndex)
            }
            transcriptionRepository.applyDefaultClusterLabels(sessionId, labels)
        }

        // Mark COMPLETED, drop the audio file path on the session, and delete the file.
        transcriptionRepository.updateSessionAudioFile(
            sessionId = sessionId,
            audioFilePath = null,
            diarizationStatus = DiarizationStatus.COMPLETED
        )
        audioStorage.deleteAudioForSession(sessionId)

        // Notify the user that diarization finished — meaningful only when the
        // app was in the background; foreground listeners on the session
        // details screen will dismiss it on resume via [DiarizationNotifier.cancel].
        notifier?.notifyDiarizationCompleted(
            sessionId = sessionId,
            sessionName = session.name,
            speakerCount = diarization.clusterCount
        )

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
