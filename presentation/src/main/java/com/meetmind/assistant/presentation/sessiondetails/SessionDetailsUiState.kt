package com.meetmind.assistant.presentation.sessiondetails

import com.meetmind.assistant.domain.model.ActionItem
import com.meetmind.assistant.domain.model.BatchInsightProgress
import com.meetmind.assistant.domain.model.DiarizationStatus
import com.meetmind.assistant.domain.model.DownloadState
import com.meetmind.assistant.domain.model.LlmInsight
import com.meetmind.assistant.domain.model.SessionPhoto
import com.meetmind.assistant.domain.model.SessionWithDetails
import com.meetmind.assistant.domain.model.TranscriptionSegment

/**
 * UI state for the session details screen.
 *
 * @property sessionDetails Complete session data with segments and insights
 * @property sessionItems List of interleaved segments and insights for display
 * @property isLoading Whether data is being loaded
 * @property error Error message if loading failed
 * @property showDeleteConfirmation Whether to show delete confirmation dialog
 * @property showHistoryInsightConfirm Whether to show the "generate AI insight copy" confirmation dialog
 * @property isGeneratingHistoryInsight Whether the history insight pipeline is running
 * @property historyInsightProgress Current progress state of the batch pipeline
 */
data class SessionDetailsUiState(
    val sessionDetails: SessionWithDetails? = null,
    val sessionItems: List<SessionDetailItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val showRenameDialog: Boolean = false,
    // Edit dialogs
    val editingSegment: TranscriptionSegment? = null,
    /** Insight being edited in the unified title+content+tasks dialog. */
    val editingInsightFull: LlmInsight? = null,
    /** ID of the segment or insight to scroll to on first load (from search navigation). */
    val highlightId: String? = null,
    /** Initial tab index: 0 = Transcript, 1 = AI Insights. Used when navigating directly to the AI Insights tab. */
    val initialTab: Int = 0,
    /** Whether to show the one-time coachmark tooltip on the AutoAwesome icon. */
    val showHistoryInsightCoachmark: Boolean = false,
    /** Whether to show the confirmation dialog before generating a history insight copy. */
    val showHistoryInsightConfirm: Boolean = false,
    /** Whether the LLM is being initialized (loaded into memory) before the pipeline starts. */
    val isInitializingLlm: Boolean = false,
    /** Whether the history insight generation pipeline is currently running. */
    val isGeneratingHistoryInsight: Boolean = false,
    /** Current progress of the batch insight pipeline; Idle when not running. */
    val historyInsightProgress: BatchInsightProgress = BatchInsightProgress.Idle,
    /**
     * BCP-47 output language selected by the user in the history insight confirm dialog.
     * Empty string = use device/session locale (auto). Populated when the confirm dialog opens.
     */
    val historyInsightOutputLanguage: String = "",
    /**
     * Intermediate (map-phase) chunk insights accumulated during history insight generation.
     * Populated progressively as each chunk completes; cleared when generation finishes or is cancelled.
     */
    val intermediateHistoryInsights: List<LlmInsight> = emptyList(),
    /**
     * The final (reduce-phase) merged insight from the history insight pipeline.
     * Set when the merge step completes; cleared when generation finishes or is cancelled.
     */
    val finalHistoryInsight: LlmInsight? = null,
    /** Action items extracted from this session's insights, loaded reactively from the DB. */
    val actionItems: List<ActionItem> = emptyList(),
    /** ID of the segment for which the speaker assignment bottom sheet is open; null = closed. */
    val speakerAssignmentSegmentId: String? = null,
    /** ID of the insight currently being regenerated; null = no regeneration in progress. */
    val regeneratingInsightId: String? = null,
    /**
     * Whether speaker diarization is currently running for this session. Drives
     * the spinner / disabled state on the "Identify speakers" button.
     */
    val isRunningDiarization: Boolean = false,
    /**
     * Number of clusters produced by the most recently completed diarization
     * run for this session, or null when no run has completed (or completed
     * but yielded no clusters). Surfaced as a confirmation toast/banner.
     */
    val lastDiarizationClusterCount: Int? = null,
    /** Whether to show the diarization model download prompt/progress dialog. */
    val showDiarizationDownloadDialog: Boolean = false,
    /** Mirror of DownloadStateManager.diarizationDownloadState for the dialog UI. */
    val diarizationDownloadState: DownloadState = DownloadState.Idle,
    /**
     * Total expected download size for the diarization model in bytes.
     * Seeded from the local estimate when the dialog opens, then upgraded to
     * the real HEAD-resolved value once the network call returns. Drives the
     * "Download (~XX MB)" button label.
     */
    val diarizationDownloadEstimatedBytes: Long = 0L,
    /** Photos captured during this session, oldest first (empty for pre-feature sessions). */
    val photos: List<SessionPhoto> = emptyList()
) {
    /**
     * Whether the "Identify speakers" toolbar button should be shown.
     *
     * Derived reactively from state so the composable re-renders whenever any
     * of the underlying fields change — unlike calling a ViewModel function from
     * the composable, which would read a stale snapshot.
     *
     * The button is shown when:
     * - Session data has loaded
     * - The session has retained audio (audioFilePath != null)
     * - Diarization isn't already blocked forever (UNAVAILABLE = no audio was ever retained)
     *
     * The button is always shown while [isRunningDiarization] so the spinner stays
     * visible; it's disabled while running.
     */
    val showDiarizationButton: Boolean get() {
        if (isRunningDiarization) return true
        val session = sessionDetails?.session ?: return false
        return session.audioFilePath != null &&
            session.diarizationStatus != DiarizationStatus.UNAVAILABLE
    }
}

/**
 * Polymorphic item for the session details list.
 */
sealed interface SessionDetailItem {
    data class Transcription(val segment: TranscriptionSegment) : SessionDetailItem
    data class Insight(val insight: LlmInsight) : SessionDetailItem
}
