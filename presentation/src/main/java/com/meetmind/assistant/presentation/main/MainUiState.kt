package com.meetmind.assistant.presentation.main

import com.meetmind.assistant.domain.model.AppSettings
import com.meetmind.assistant.domain.model.BatchInsightProgress
import com.meetmind.assistant.domain.model.InsightStrategy
import com.meetmind.assistant.domain.model.LlmInsight
import com.meetmind.assistant.domain.model.RecordingMode
import com.meetmind.assistant.domain.model.TranscriptionSegment

/**
 * UI state for the main screen.
 *
 * @property isRecording Whether audio recording and STT is currently active
 * @property isFinalizingSession Whether a final LLM insight is being generated after stop.
 *   Recording is already stopped (isRecording = false) but the session has not fully
 *   concluded yet. The UI shows a shimmer placeholder while this is true.
 * @property completedSegments List of completed transcription segments (isComplete=true)
 * @property currentPartialSegment Current partial transcription segment being built (isComplete=false)
 * @property insights List of LLM-generated insights
 * @property settings Current application settings
 * @property error Error message to display, null if no error
 * @property llmStatus Status message for LLM (e.g., "Ready", "Processing", "Unavailable")
 * @property isDownloadingModel Whether LLM model is currently being downloaded
 * @property downloadProgress Download progress percentage (0-100)
 * @property insightStrategy The insight generation strategy for the current session
 * @property batchProgress Progress state of the end-of-session batch pipeline
 * @property recordingMode The active recording mode for the current session
 */
data class MainUiState(
    val isRecording: Boolean = false,
    val isFinalizingSession: Boolean = false,
    val insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    val batchProgress: BatchInsightProgress = BatchInsightProgress.Idle,
    val recordingMode: RecordingMode = RecordingMode.SHORT_MEETING,
    val isInitializing: Boolean = true,
    val isInitializingLlm: Boolean = false,
    val isMicPermissionDenied: Boolean = false,
    val completedSegments: List<TranscriptionSegment> = emptyList(),
    val currentPartialSegment: TranscriptionSegment? = null,
    val insights: List<LlmInsight> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val error: String? = null,
    val llmStatus: String = "Ready",
    val isLlmModelAvailable: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadSpeedMbps: Float = 0f,
    val downloadEtaSeconds: Int = 0,
    val recordingDurationMillis: Long = 0,
    val regeneratingInsightId: String? = null,
    /**
     * True when the user started recording in REAL_TIME mode but the device was already
     * thermally critical so we silently downgraded to END_OF_SESSION. The UI shows a
     * dismissible banner explaining why real-time insights are off this session.
     */
    val thermalDowngradeBannerVisible: Boolean = false,
    /**
     * True when this is a long-meeting recording on a vendor known to ignore the
     * Android foreground-service contract (Xiaomi, Oppo, Vivo, Huawei, etc.) AND
     * the user has not yet seen the whitelist prompt. The dialog explains the
     * issue and offers a button that deep-links to the OEM's autostart settings.
     */
    val batteryWhitelistPromptVisible: Boolean = false,
    /**
     * True when the active LLM variant supports vision AND both its base model and
     * mmproj adapter are on disk. Gates the camera button in the recording top bar.
     */
    val isVisionCapable: Boolean = false,
    /**
     * True while a captured photo is being analyzed by the vision model.
     * The camera button is disabled and an "Analyzing photo…" banner is shown.
     */
    val isAnalyzingPhoto: Boolean = false
) {
    /**
     * Returns all segments for display: completed + current partial (if exists).
     * Use this in UI to show the full transcription without duplicates.
     */
    val allSegments: List<TranscriptionSegment>
        get() = completedSegments + listOfNotNull(currentPartialSegment)

    /**
     * Returns insights sorted by timestamp (creation time) instead of arrival time.
     * This prevents insights from appearing out of order when longer insights
     * take more time to generate than shorter ones.
     */
    val sortedInsights: List<LlmInsight>
        get() = insights.sortedBy { it.timestamp }
}
