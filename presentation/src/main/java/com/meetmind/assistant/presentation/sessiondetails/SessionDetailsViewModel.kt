package com.meetmind.assistant.presentation.sessiondetails

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.domain.model.BatchInsightProgress
import com.meetmind.assistant.domain.repository.ActionItemRepository
import com.meetmind.assistant.domain.repository.LlmRepository
import com.meetmind.assistant.domain.repository.SettingsRepository
import com.meetmind.assistant.domain.service.LlmProcessingServiceController
import com.meetmind.assistant.domain.usecase.llm.GenerateHistoryInsightUseCase
import com.meetmind.assistant.domain.usecase.llm.GenerateFinalInsightUseCase
import com.meetmind.assistant.domain.usecase.llm.InitializeLlmUseCase
import com.meetmind.assistant.domain.usecase.llm.RegenerateInsightUseCase
import com.meetmind.assistant.domain.usecase.transcription.DeleteSessionUseCase
import com.meetmind.assistant.domain.usecase.transcription.GetSessionDetailsUseCase
import com.meetmind.assistant.domain.usecase.transcription.RenameSessionUseCase
import com.meetmind.assistant.domain.usecase.transcription.UpdateInsightContentUseCase
import com.meetmind.assistant.domain.usecase.transcription.UpdateSegmentTextUseCase
import com.meetmind.assistant.domain.usecase.transcription.UpdateSegmentSpeakerUseCase
import com.meetmind.assistant.domain.usecase.transcription.RunDiarizationUseCase
import com.meetmind.assistant.domain.repository.DiarizationRepository
import com.meetmind.assistant.domain.repository.TranscriptionRepository
import com.meetmind.assistant.domain.model.DiarizationStatus
import com.meetmind.assistant.domain.model.DownloadState
import com.meetmind.assistant.domain.model.SessionPhoto
import com.meetmind.assistant.data.service.AndroidDownloadManager
import com.meetmind.assistant.data.service.DownloadStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the session details screen.
 *
 * Displays complete information about a single transcription session
 * including all segments and insights.
 *
 * @property getSessionDetailsUseCase Use case for retrieving session details
 * @property deleteSessionUseCase Use case for deleting the session
 * @property savedStateHandle Navigation arguments (contains sessionId)
 */
@HiltViewModel
class SessionDetailsViewModel @Inject constructor(
    private val getSessionDetailsUseCase: GetSessionDetailsUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val renameSessionUseCase: RenameSessionUseCase,
    private val updateSegmentTextUseCase: UpdateSegmentTextUseCase,
    private val updateSegmentSpeakerUseCase: UpdateSegmentSpeakerUseCase,
    private val updateInsightContentUseCase: UpdateInsightContentUseCase,
    private val generateHistoryInsightUseCase: GenerateHistoryInsightUseCase,
    private val generateFinalInsightUseCase: GenerateFinalInsightUseCase,
    private val initializeLlmUseCase: InitializeLlmUseCase,
    private val regenerateInsightUseCase: RegenerateInsightUseCase,
    private val llmRepository: LlmRepository,
    private val settingsRepository: SettingsRepository,
    private val llmProcessingServiceController: LlmProcessingServiceController,
    private val actionItemRepository: ActionItemRepository,
    private val crashReporter: com.meetmind.assistant.domain.monitor.CrashReporter,
    private val runDiarizationUseCase: RunDiarizationUseCase,
    private val diarizationRepository: DiarizationRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val androidDownloadManager: AndroidDownloadManager,
    private val downloadStateManager: DownloadStateManager,
    private val diarizationNotifier: com.meetmind.assistant.domain.notification.DiarizationNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "SessionDetailsVM"
    }

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"]) {
        "Session ID is required"
    }

    // Auto-trigger flags: set when the user pressed "Generate AI Insight" on the sessions list.
    // The dialog there has already collected the output language, so we open the confirm
    // dialog here pre-populated with that language for a one-tap confirmation.
    private val autoAnalyze: Boolean = savedStateHandle["autoAnalyze"] ?: false
    private val autoAnalyzeOutputLanguage: String? = savedStateHandle["autoAnalyzeOutputLanguage"]
    // Single-shot guard so the dialog opens only on first composition, not on every recomposition
    // or after the user dismisses it.
    private var autoAnalyzeHandled: Boolean = false

    private val _uiState = MutableStateFlow(
        SessionDetailsUiState(
            highlightId = savedStateHandle["highlightId"],
            initialTab = savedStateHandle["initialTab"] ?: 0
        )
    )
    val uiState: StateFlow<SessionDetailsUiState> = _uiState.asStateFlow()

    // Tracks the active history-insight generation job so it can be cancelled.
    private var historyInsightJob: Job? = null
    // ID of the copy session created during history insight generation.
    // Stored so it can be deleted if the user cancels mid-pipeline.
    private var pendingHistorySessionId: String? = null

    init {
        loadSessionDetails()
        loadActionItems()
        loadPhotos()
        checkHistoryInsightCoachmark()
        maybeAutoTriggerHistoryInsight()
        observeDiarizationDownload()
        // If the user opened this screen by tapping a "diarization completed"
        // notification, dismiss it now so they don't see a stale entry in the
        // tray for a session they're already viewing.
        diarizationNotifier.cancel(sessionId)
    }

    /**
     * Mirror the global [DownloadStateManager.diarizationDownloadState] into our
     * UiState so the dialog can drive its own progress bar without a second
     * subscription, and so completion can transparently trigger
     * [runDiarizationInternal] when the user originally requested diarization
     * but had to wait through the model download first.
     */
    private fun observeDiarizationDownload() {
        viewModelScope.launch {
            downloadStateManager.diarizationDownloadState.collect { state ->
                _uiState.update { it.copy(diarizationDownloadState = state) }
                if (state is DownloadState.Completed && pendingDiarizationAfterDownload) {
                    pendingDiarizationAfterDownload = false
                    // Close the dialog and kick off the actual diarization run.
                    _uiState.update { it.copy(showDiarizationDownloadDialog = false) }
                    downloadStateManager.resetDiarizationState()
                    runDiarizationInternal()
                }
            }
        }
    }

    /**
     * True iff the user tapped "Identify speakers" while the model was missing,
     * triggering a download. On [DownloadState.Completed] we automatically run
     * diarization without requiring a second tap.
     */
    private var pendingDiarizationAfterDownload: Boolean = false

    /**
     * Localized format string for default cluster labels (e.g. "Speaker %1$d").
     * Set by the composable via [setSpeakerClusterLabelFormat] so the use case
     * can apply localized "Speaker 1", "Speaker 2", … without the domain
     * layer needing access to Android resources. Falls back to a non-localized
     * default for tests and pre-set callsites.
     */
    private var speakerClusterLabelFormat: String = "Speaker %1\$d"

    /**
     * Called from the composable on first composition so that the use case
     * can produce localized cluster labels. The composable resolves the
     * format string via [androidx.compose.ui.res.stringResource].
     */
    fun setSpeakerClusterLabelFormat(format: String) {
        speakerClusterLabelFormat = format
    }

    /**
     * If the screen was opened with `autoAnalyze=true` (from the sessions list AI button),
     * wait until session details have loaded, then open the confirm dialog with the
     * output language the user already chose pre-selected. Single-shot.
     */
    private fun maybeAutoTriggerHistoryInsight() {
        if (!autoAnalyze || autoAnalyzeHandled) return
        viewModelScope.launch {
            // Wait for session details to load so showHistoryInsightConfirm() can read
            // the session's saved outputLanguage as a fallback before we override it.
            uiState
                .filter { it.sessionDetails != null }
                .first()
            if (autoAnalyzeHandled) return@launch
            autoAnalyzeHandled = true
            showHistoryInsightConfirm()
            // Override the language with the one the user selected on the sessions list dialog.
            autoAnalyzeOutputLanguage
                ?.takeIf { it.isNotBlank() }
                ?.let { setHistoryInsightOutputLanguage(it) }
        }
    }

    private fun loadActionItems() {
        viewModelScope.launch {
            actionItemRepository.getBySession(sessionId)
                .catch { /* non-fatal */ }
                .collect { items ->
                    _uiState.update { it.copy(actionItems = items) }
                }
        }
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            transcriptionRepository.getPhotosForSession(sessionId)
                .catch { /* non-fatal */ }
                .collect { photos ->
                    _uiState.update { it.copy(photos = photos) }
                }
        }
    }

    fun toggleActionItem(id: String, isDone: Boolean) {
        viewModelScope.launch {
            actionItemRepository.toggle(id, isDone)
        }
    }

    fun deleteActionItem(id: String) {
        viewModelScope.launch {
            actionItemRepository.delete(id)
        }
    }

    /**
     * Shows the history-insight coachmark on first visit, then immediately persists
     * the "shown" flag so it never appears again on subsequent visits.
     */
    private fun checkHistoryInsightCoachmark() {
        viewModelScope.launch {
            val alreadyShown = settingsRepository.getSettings().first().hasShownHistoryInsightCoachmark
            if (!alreadyShown) {
                _uiState.update { it.copy(showHistoryInsightCoachmark = true) }
                settingsRepository.markHistoryInsightCoachmarkShown()
            }
        }
    }

    /**
     * Hides the history-insight coachmark (user dismissed it or auto-dismiss fired).
     */
    fun dismissHistoryInsightCoachmark() {
        _uiState.update { it.copy(showHistoryInsightCoachmark = false) }
    }

    /**
     * Load session details from the repository.
     */
    /**
     * Load session details from the repository.
     */
    private fun loadSessionDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getSessionDetailsUseCase(sessionId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load session: ${e.message}"
                        )
                    }
                }
                .collect { details ->
                    val items = if (details != null) createSessionItems(details) else emptyList()
                    
                    _uiState.update {
                        it.copy(
                            sessionDetails = details,
                            sessionItems = items,
                            isLoading = false,
                            error = if (details == null) "Session not found" else null
                        )
                    }
                }
        }
    }

    /**
     * Merges segments and insights into a single chronological list
     * using a two-pointer approach based on timestamps.
     */
    private fun createSessionItems(details: com.meetmind.assistant.domain.model.SessionWithDetails): List<SessionDetailItem> {
        val segments = details.segments.sortedBy { it.timestamp }
        val insights = details.insights.sortedBy { it.timestamp }

        // For END_OF_SESSION strategy:
        //  - Final insights (sourceSegmentIds empty) go to the top so the user sees the summary first.
        //  - Intermediate insights (sourceSegmentIds non-empty) are interleaved chronologically with
        //    segments, positioned after the last segment they cover.
        if (details.session.insightStrategy == com.meetmind.assistant.domain.model.InsightStrategy.END_OF_SESSION) {
            val finalInsights = insights.filter { it.sourceSegmentIds.isEmpty() }
            val intermediateInsights = insights.filter { it.sourceSegmentIds.isNotEmpty() }

            if (intermediateInsights.isEmpty()) {
                // No intermediates: all insights at top, all segments below (legacy / single-chunk).
                return finalInsights.map { SessionDetailItem.Insight(it) } +
                        segments.map { SessionDetailItem.Transcription(it) }
            }

            // Build a set for O(1) lookup: last segment ID of each intermediate insight.
            // An intermediate insight is placed immediately after the last segment in its chunk.
            val lastSegmentIdToInsight = intermediateInsights
                .filter { it.sourceSegmentIds.isNotEmpty() }
                .associateBy { it.sourceSegmentIds.last() }

            val body = mutableListOf<SessionDetailItem>()
            for (segment in segments) {
                body.add(SessionDetailItem.Transcription(segment))
                lastSegmentIdToInsight[segment.id]?.let { body.add(SessionDetailItem.Insight(it)) }
            }

            return finalInsights.map { SessionDetailItem.Insight(it) } + body
        }

        val items = mutableListOf<SessionDetailItem>()
        var si = 0
        var ii = 0

        while (si < segments.size && ii < insights.size) {
            if (segments[si].timestamp <= insights[ii].timestamp) {
                items.add(SessionDetailItem.Transcription(segments[si++]))
            } else {
                items.add(SessionDetailItem.Insight(insights[ii++]))
            }
        }
        while (si < segments.size) {
            items.add(SessionDetailItem.Transcription(segments[si++]))
        }
        while (ii < insights.size) {
            items.add(SessionDetailItem.Insight(insights[ii++]))
        }

        return items
    }

    /**
     * Show the rename dialog.
     */
    fun showRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = true) }
    }

    /**
     * Hide the rename dialog without saving.
     */
    fun hideRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false) }
    }

    /**
     * Rename this session.
     *
     * @param newName New name; blank clears the name
     */
    fun renameSession(newName: String) {
        viewModelScope.launch {
            renameSessionUseCase(sessionId, newName)
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = "Failed to rename session: ${e.message}")
                    }
                }
            _uiState.update { it.copy(showRenameDialog = false) }
            // sessionDetails refreshes automatically via the existing Flow
        }
    }

    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    /**
     * Hide delete confirmation dialog.
     */
    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    /**
     * Delete this session and navigate back.
     *
     * @param onDeleted Callback invoked after successful deletion
     */
    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteSessionUseCase(sessionId)
                .onSuccess {
                    onDeleted()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = "Failed to delete session: ${e.message}",
                            showDeleteConfirmation = false
                        )
                    }
                }
        }
    }

    /**
     * Export session as plain text.
     *
     * Returns the full transcription text.
     */
    fun exportAsText(): String {
        val details = _uiState.value.sessionDetails ?: return ""
        val session = details.session

        return buildString {
            appendLine("=".repeat(50))
            appendLine("TRANSCRIPTION SESSION")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Name: ${session.name ?: "(Unnamed)"}")
            appendLine("Date: ${formatTimestamp(session.createdAt)}")
            appendLine("Segments: ${details.completeSegmentCount}")
            appendLine("Insights: ${details.insights.size}")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("TRANSCRIPTION")
            appendLine("=".repeat(50))
            appendLine()
            appendLine(details.fullTranscription)

            if (details.insights.isNotEmpty()) {
                appendLine()
                appendLine("=".repeat(50))
                appendLine("LLM INSIGHTS")
                appendLine("=".repeat(50))
                appendLine()
                details.insights.forEachIndexed { index, insight ->
                    appendLine("${index + 1}. ${insight.content}")
                    appendLine()
                }
            }
        }
    }

    /**
     * Export session as a polished Markdown summary for sharing.
     *
     * Layout (top → bottom):
     *  1. Title + metadata header (date, duration, segment count)
     *  2. **TL;DR** — pinned single-paragraph overview (first insight's summary)
     *  3. Per-insight sections with `### Action Items` checklists
     *  4. Aggregated `## Action Items` checklist combining all insights' tasks
     *  5. Collapsed `<details>` block with the full timestamped transcript
     *     (YouTube-style `[mm:ss]` offsets, **bold** speaker labels)
     *
     * Includes only the final/summary insights (sourceSegmentIds empty) for END_OF_SESSION
     * sessions, or all insights for REAL_TIME sessions — mirroring what the AI Insights tab shows.
     */
    fun exportAsSummaryMarkdown(): String {
        val details = _uiState.value.sessionDetails ?: return ""
        val session = details.session

        val summaryInsights = if (session.insightStrategy ==
            com.meetmind.assistant.domain.model.InsightStrategy.END_OF_SESSION
        ) {
            details.insights.filter { it.sourceSegmentIds.isEmpty() }
                .ifEmpty { details.insights }
        } else {
            details.insights
        }.sortedBy { it.timestamp }

        // Aggregate every action item across all insights for a single combined checklist.
        // Deduped (case-insensitive trim) so repeated tasks across chunked insights
        // collapse into one row.
        val allTasks = summaryInsights
            .flatMap { parseTasksToStrings(it.tasks) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        return buildString {
            // ── Header ─────────────────────────────────────────────────────
            appendLine("# ${session.name ?: "(Unnamed Session)"}")
            appendLine()
            appendLine("> 📅 ${formatTimestamp(session.createdAt)}  ")
            if (session.durationMs > 0L) {
                appendLine("> ⏱️ ${formatDurationHuman(session.durationMs)}  ")
            }
            appendLine("> 💬 ${details.completeSegmentCount} segments · ${summaryInsights.size} insights")
            appendLine()

            // ── TL;DR (pinned at top) ──────────────────────────────────────
            val firstSummary = summaryInsights.firstOrNull()
                ?.let { extractSummaryFromContent(it.content) }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (firstSummary != null) {
                appendLine("## TL;DR")
                appendLine(firstSummary.lines().first().take(280)) // first sentence-ish
                appendLine()
                appendLine("---")
                appendLine()
            }

            // ── Per-insight detail sections ────────────────────────────────
            summaryInsights.forEach { insight ->
                val title = insight.title?.takeIf { it.isNotBlank() } ?: "Summary"
                appendLine("## $title")
                appendLine(extractSummaryFromContent(insight.content))

                val tasks = parseTasksToStrings(insight.tasks)
                if (tasks.isNotEmpty()) {
                    appendLine()
                    appendLine("### Action Items")
                    tasks.forEach { task -> appendLine("- [ ] $task") }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }

            // ── Combined Action Items (only if multiple insights contributed) ─
            if (allTasks.size > 1 && summaryInsights.size > 1) {
                appendLine("## All Action Items")
                allTasks.forEach { task -> appendLine("- [ ] $task") }
                appendLine()
                appendLine("---")
                appendLine()
            }

            // ── Full transcript (collapsed by default in GitHub/Slack-style renderers) ─
            val segments = details.segments.filter { it.isComplete }
            if (segments.isNotEmpty()) {
                appendLine("<details>")
                appendLine("<summary><strong>Full Transcript</strong> (${segments.size} segments)</summary>")
                appendLine()
                segments.forEach { seg ->
                    val offsetMs = (seg.timestamp - session.createdAt).coerceAtLeast(0L)
                    val ts = formatOffsetTimestamp(offsetMs)
                    val speaker = seg.speaker?.takeIf { it.isNotBlank() }
                    val line = if (speaker != null) {
                        "`[$ts]` **$speaker:** ${seg.text.trim()}"
                    } else {
                        "`[$ts]` ${seg.text.trim()}"
                    }
                    appendLine(line)
                    appendLine()
                }
                appendLine("</details>")
                appendLine()
            }

            append("*Generated by MeetMind Assistant*")
        }
    }

    /**
     * Export session as a PDF file and return its path inside [Context.filesDir]/exports/.
     *
     * Layout (one page per logical section, content wraps across pages automatically):
     *  - Title page: session name, date, duration, segment/insight counts
     *  - Insights pages: one section per insight — title, summary paragraph, action items
     *  - Transcript pages: timestamped lines with optional bold speaker labels
     *
     * Returns the absolute path of the written file, or null if there is nothing to export.
     * The caller is responsible for sharing the file via FileProvider.
     */
    fun exportAsPdf(context: Context): String? {
        val details = _uiState.value.sessionDetails ?: return null
        val session = details.session

        val summaryInsights = if (session.insightStrategy ==
            com.meetmind.assistant.domain.model.InsightStrategy.END_OF_SESSION
        ) {
            details.insights.filter { it.sourceSegmentIds.isEmpty() }
                .ifEmpty { details.insights }
        } else {
            details.insights
        }.sortedBy { it.timestamp }

        val segments = details.segments.filter { it.isComplete }

        // ── PDF layout constants ───────────────────────────────────────────────
        val pageWidth  = 595   // A4 points (72 dpi)
        val pageHeight = 842
        val marginL    = 56f
        val marginR    = pageWidth - 56f
        val marginT    = 60f
        val marginB    = pageHeight - 60f
        val lineH      = 18f   // base line height for body text

        // ── Paints ────────────────────────────────────────────────────────────
        val paintTitle = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintHeading = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintSubHeading = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintBody = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val paintMeta = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }
        val paintAccent = Paint().apply {
            color = 0xFF5B4FCF.toInt()  // brand primary
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintHRule = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val doc = PdfDocument()

        // ── Helpers ────────────────────────────────────────────────────────────
        var pageIndex = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageIndex).create())
        var canvas: Canvas = page.canvas
        var y = marginT

        fun newPage() {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageIndex).create())
            canvas = page.canvas
            y = marginT
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > marginB) newPage()
        }

        /** Wrap [text] into lines that fit within [maxWidth] using [paint]. */
        fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in words) {
                val test = if (line.isEmpty()) word else "${line} $word"
                if (paint.measureText(test) <= maxWidth) {
                    line.append(if (line.isEmpty()) word else " $word")
                } else {
                    if (line.isNotEmpty()) lines.add(line.toString())
                    line = StringBuilder(word)
                }
            }
            if (line.isNotEmpty()) lines.add(line.toString())
            return lines.ifEmpty { listOf("") }
        }

        fun drawWrapped(text: String, paint: Paint, x: Float = marginL, lineSpacing: Float = lineH) {
            val lines = wrapText(text, paint, marginR - x)
            for (l in lines) {
                ensureSpace(lineSpacing)
                canvas.drawText(l, x, y, paint)
                y += lineSpacing
            }
        }

        fun drawHRule() {
            ensureSpace(8f)
            canvas.drawLine(marginL, y, marginR, y, paintHRule)
            y += 8f
        }

        // ── Title page ────────────────────────────────────────────────────────
        y = marginT + 20f
        drawWrapped(session.name ?: "(Unnamed Session)", paintTitle, lineSpacing = 28f)
        y += 4f
        drawWrapped(formatTimestamp(session.createdAt), paintMeta)
        if (session.durationMs > 0L) {
            drawWrapped("Duration: ${formatDurationHuman(session.durationMs)}", paintMeta)
        }
        drawWrapped(
            "${details.completeSegmentCount} transcript segments · ${summaryInsights.size} insights",
            paintMeta
        )
        y += 8f
        drawHRule()

        // ── Insights ──────────────────────────────────────────────────────────
        if (summaryInsights.isNotEmpty()) {
            y += 10f
            ensureSpace(24f)
            canvas.drawText("AI Insights", marginL, y, paintHeading)
            y += lineH + 4f
            drawHRule()

            summaryInsights.forEach { insight ->
                val title = insight.title?.takeIf { it.isNotBlank() } ?: "Summary"
                ensureSpace(lineH + 4f)
                canvas.drawText(title, marginL, y, paintSubHeading)
                y += lineH + 2f

                val summary = extractSummaryFromContent(insight.content).trim()
                if (summary.isNotBlank()) {
                    drawWrapped(summary, paintBody, lineSpacing = 15f)
                }

                val tasks = parseTasksToStrings(insight.tasks)
                if (tasks.isNotEmpty()) {
                    y += 4f
                    ensureSpace(lineH)
                    canvas.drawText("Action Items", marginL, y, paintSubHeading)
                    y += lineH
                    tasks.forEach { task ->
                        ensureSpace(15f)
                        canvas.drawText("☐  $task", marginL + 8f, y, paintBody)
                        y += 15f
                    }
                }
                y += 6f
                drawHRule()
            }
        }

        // ── Transcript ────────────────────────────────────────────────────────
        if (segments.isNotEmpty()) {
            y += 10f
            ensureSpace(24f)
            canvas.drawText("Full Transcript", marginL, y, paintHeading)
            y += lineH + 4f
            drawHRule()

            segments.forEach { seg ->
                val offsetMs = (seg.timestamp - session.createdAt).coerceAtLeast(0L)
                val ts = formatOffsetTimestamp(offsetMs)
                val speaker = seg.speaker?.takeIf { it.isNotBlank() }

                ensureSpace(15f)
                // Timestamp in accent color
                canvas.drawText("[$ts]", marginL, y, paintAccent)
                val tsWidth = paintAccent.measureText("[$ts]") + 6f

                if (speaker != null) {
                    canvas.drawText("$speaker:", marginL + tsWidth, y, paintSubHeading)
                    val spWidth = paintSubHeading.measureText("$speaker:") + 4f
                    // First line of the text on the same row as timestamp
                    val textX = marginL + tsWidth + spWidth
                    val textMaxW = marginR - textX
                    val textLines = wrapText(seg.text.trim(), paintBody, textMaxW)
                    canvas.drawText(textLines[0], textX, y, paintBody)
                    y += 15f
                    for (i in 1 until textLines.size) {
                        ensureSpace(15f)
                        canvas.drawText(textLines[i], marginL + tsWidth + spWidth, y, paintBody)
                        y += 15f
                    }
                } else {
                    val textX = marginL + tsWidth
                    val textLines = wrapText(seg.text.trim(), paintBody, marginR - textX)
                    canvas.drawText(textLines[0], textX, y, paintBody)
                    y += 15f
                    for (i in 1 until textLines.size) {
                        ensureSpace(15f)
                        canvas.drawText(textLines[i], textX, y, paintBody)
                        y += 15f
                    }
                }
            }
        }

        // ── Footer on last page ───────────────────────────────────────────────
        ensureSpace(20f)
        drawHRule()
        canvas.drawText("Generated by MeetMind Assistant", marginL, y, paintMeta)
        doc.finishPage(page)

        // ── Write to file ─────────────────────────────────────────────────────
        val exportsDir = java.io.File(context.filesDir, "exports")
        exportsDir.mkdirs()
        val safeName = (session.name ?: "session")
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .take(40)
            .replace(' ', '_')
        val outFile = java.io.File(exportsDir, "${safeName}_${session.id.take(8)}.pdf")
        try {
            outFile.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return outFile.absolutePath
    }

    /**
     * Format a millisecond offset as `[mm:ss]` or `[hh:mm:ss]` for a transcript timestamp.
     * Mirrors YouTube's chapter-link convention so pasted transcripts are scannable.
     */
    private fun formatOffsetTimestamp(offsetMs: Long): String {
        val totalSec = offsetMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    /**
     * Format a millisecond duration as a human-readable string ("12 min 34 sec").
     * Used in the export header where a precise machine-readable form isn't needed.
     */
    private fun formatDurationHuman(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else  -> "${s}s"
        }
    }

    /**
     * Extract the human-readable summary from an insight's content JSON.
     * The content column stores either a JSON object `{"summary": "..."}` or raw text.
     */
    private fun extractSummaryFromContent(content: String): String {
        return try {
            val json = org.json.JSONObject(content)
            json.optString("summary", content)
        } catch (_: Exception) {
            content
        }
    }

    /**
     * Parse a tasks JSON array string into a list of plain strings.
     * Supports `["task"]` and `[{"description": "task"}]` formats.
     */
    private fun parseTasksToStrings(tasksJson: String?): List<String> {
        if (tasksJson == null) return emptyList()
        return try {
            val arr = org.json.JSONArray(tasksJson)
            (0 until arr.length()).mapNotNull { i ->
                when (val item = arr.get(i)) {
                    is org.json.JSONObject -> item.optString("description", "").takeIf { it.isNotBlank() }
                    else -> item.toString().takeIf { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ========== Segment Editing ==========

    /** Open edit dialog for a transcription segment. */
    fun showEditSegmentDialog(segment: com.meetmind.assistant.domain.model.TranscriptionSegment) {
        _uiState.update { it.copy(editingSegment = segment) }
    }

    /** Dismiss segment edit dialog without saving. */
    fun hideEditSegmentDialog() {
        _uiState.update { it.copy(editingSegment = null) }
    }

    /** Persist updated segment text. */
    fun saveSegmentText(segmentId: String, newText: String) {
        viewModelScope.launch {
            updateSegmentTextUseCase(segmentId, newText)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Failed to update transcription: ${e.message}") }
                }
            _uiState.update { it.copy(editingSegment = null) }
        }
    }

    // ========== Speaker Assignment ==========

    /** Open the speaker assignment bottom sheet for a segment. */
    fun showSpeakerAssignment(segmentId: String) {
        _uiState.update { it.copy(speakerAssignmentSegmentId = segmentId) }
    }

    /** Close the speaker assignment bottom sheet without saving. */
    fun hideSpeakerAssignment() {
        _uiState.update { it.copy(speakerAssignmentSegmentId = null) }
    }

    /**
     * Assign or clear a speaker label on a segment.
     *
     * Cluster-aware propagation: if the segment has a diarization
     * [TranscriptionSegment.speakerCluster] (set by [runDiarization]), the
     * label is propagated to every segment of the same session that shares
     * that cluster. This means tagging "Boss" on one utterance auto-labels
     * every other segment the diarization model placed in the same speaker
     * bucket. Most-recent-write-wins on conflicts within a cluster.
     *
     * Segments without a cluster (legacy sessions, segments with no offsets,
     * or sessions where diarization hasn't run) fall back to single-segment
     * update — same behavior as before this feature.
     */
    fun setSpeaker(segmentId: String, speaker: String?) {
        viewModelScope.launch {
            // Look up the cluster on the in-memory segment snapshot before mutating.
            val cluster = _uiState.value.sessionDetails
                ?.segments
                ?.firstOrNull { it.id == segmentId }
                ?.speakerCluster

            if (cluster != null) {
                transcriptionRepository.propagateSpeakerLabelByCluster(sessionId, cluster, speaker)
                    .onFailure { e ->
                        _uiState.update { it.copy(error = "Failed to assign speaker: ${e.message}") }
                    }
            } else {
                updateSegmentSpeakerUseCase(segmentId, speaker)
                    .onFailure { e ->
                        _uiState.update { it.copy(error = "Failed to assign speaker: ${e.message}") }
                    }
            }
            _uiState.update { it.copy(speakerAssignmentSegmentId = null) }
        }
    }

    // ========== Speaker Diarization ==========

    /**
     * Entry point for the "Identify speakers" button.
     *
     * Branches based on model availability:
     *   - Model present → run diarization immediately
     *   - Model missing → open the download confirmation dialog. If the user
     *     confirms, [confirmDiarizationModelDownload] kicks off the download;
     *     [observeDiarizationDownload] auto-runs diarization when it completes.
     *
     * Failure modes surfaced as user-facing errors:
     *   - No retained audio (session predates the feature)
     *   - Native pipeline failure
     */
    fun runDiarization() {
        if (_uiState.value.isRunningDiarization) return
        if (diarizationRepository.isModelAvailable()) {
            runDiarizationInternal()
        } else {
            // Seed the dialog with the local size estimate immediately so the
            // user sees a real "~XX MB" label at first paint. Then in a
            // background coroutine, ask the network for the precise total via
            // HEAD requests and overwrite the estimate when it lands.
            val initialEstimate = androidDownloadManager.diarizationEstimatedTotalSize()
            _uiState.update {
                it.copy(
                    showDiarizationDownloadDialog = true,
                    diarizationDownloadEstimatedBytes = initialEstimate
                )
            }
            viewModelScope.launch {
                val real = runCatching { androidDownloadManager.diarizationRemoteTotalSize() }
                    .getOrNull()
                if (real != null && real > 0) {
                    _uiState.update { it.copy(diarizationDownloadEstimatedBytes = real) }
                }
            }
        }
    }

    /**
     * Actually run the diarization pipeline. Always called with the model
     * known-present (either initially, or after the download flow).
     */
    private fun runDiarizationInternal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningDiarization = true, error = null) }
            val result = runDiarizationUseCase(sessionId) { oneBasedIndex ->
                // Format the localized "Speaker N" label. Use String.format
                // explicitly to keep the Android Resources dependency out of
                // the domain layer.
                String.format(speakerClusterLabelFormat, oneBasedIndex)
            }
            result
                .onSuccess { diarization ->
                    _uiState.update {
                        it.copy(
                            isRunningDiarization = false,
                            lastDiarizationClusterCount = diarization.clusterCount
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isRunningDiarization = false,
                            error = e.message ?: "Speaker diarization failed."
                        )
                    }
                }
        }
    }

    /**
     * User confirmed the download prompt. Kick off the model download and
     * remember to auto-run diarization once it lands ([pendingDiarizationAfterDownload]).
     * The dialog stays open and switches to a progress view driven by
     * [SessionDetailsUiState.diarizationDownloadState].
     */
    fun confirmDiarizationModelDownload() {
        pendingDiarizationAfterDownload = true
        androidDownloadManager.startDiarizationDownload()
    }

    /**
     * User dismissed the download dialog (or tapped Cancel during download).
     * Clears the pending flag and cancels any in-flight download.
     */
    fun cancelDiarizationModelDownload() {
        pendingDiarizationAfterDownload = false
        androidDownloadManager.cancelDiarizationDownload()
        _uiState.update { it.copy(showDiarizationDownloadDialog = false) }
    }

    /** Dismiss the post-diarization "found N speakers" banner. */
    fun dismissDiarizationResultBanner() {
        _uiState.update { it.copy(lastDiarizationClusterCount = null) }
    }

    /**
     * Whether the "Identify speakers" button should be available to the user.
     *
     * Note: this no longer requires [DiarizationRepository.isModelAvailable] —
     * the user can tap the button to *trigger* the model download. We only
     * gate on conditions that can never be fixed by downloading models:
     * the session must have retained audio, must not already be running,
     * and must not have status UNAVAILABLE.
     */
    fun canRunDiarization(): Boolean {
        val session = _uiState.value.sessionDetails?.session ?: return false
        return session.audioFilePath != null &&
            session.diarizationStatus != DiarizationStatus.RUNNING &&
            session.diarizationStatus != DiarizationStatus.UNAVAILABLE &&
            !_uiState.value.isRunningDiarization
    }

    // ========== Insight Editing (unified) ==========

    /** Open the unified edit dialog for title, content, and tasks of an insight. */
    fun showEditInsightFullDialog(insight: com.meetmind.assistant.domain.model.LlmInsight) {
        _uiState.update { it.copy(editingInsightFull = insight) }
    }

    /** Dismiss the unified insight edit dialog without saving. */
    fun hideEditInsightFullDialog() {
        _uiState.update { it.copy(editingInsightFull = null) }
    }

    /**
     * Persist updated title, content, and tasks for an insight.
     *
     * @param insightId ID of the insight to update.
     * @param newTitle Updated title; blank clears it.
     * @param patchedContent Full updated content string (already patched by the caller).
     * @param tasksText Newline-delimited task strings; empty string clears all tasks.
     */
    fun saveInsightFull(insightId: String, newTitle: String, patchedContent: String, tasksText: String) {
        viewModelScope.launch {
            val taskLines = tasksText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val tasksJson = if (taskLines.isEmpty()) null
                            else org.json.JSONArray().apply { taskLines.forEach { put(it) } }.toString()

            updateInsightContentUseCase.updateTitle(insightId, newTitle.ifBlank { null })
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to update insight: ${e.message}") } }
            updateInsightContentUseCase.updateContent(insightId, patchedContent)
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to update insight: ${e.message}") } }
            updateInsightContentUseCase.updateTasks(insightId, tasksJson)
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to update insight: ${e.message}") } }

            _uiState.update { it.copy(editingInsightFull = null) }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ========== History Insight Generation ==========

    /** Show the confirmation dialog for generating an AI insight copy. */
    fun showHistoryInsightConfirm() {
        // Pre-populate the language selector with the session's saved outputLanguage (if any),
        // so returning users see their original choice; new sessions default to device locale.
        val sessionOutputLanguage = _uiState.value.sessionDetails?.session?.outputLanguage ?: ""
        _uiState.update {
            it.copy(
                showHistoryInsightConfirm = true,
                historyInsightOutputLanguage = sessionOutputLanguage
            )
        }
    }

    /** Dismiss the confirmation dialog without starting generation. */
    fun hideHistoryInsightConfirm() {
        _uiState.update { it.copy(showHistoryInsightConfirm = false) }
    }

    /** Update the output language chosen in the history insight confirm dialog. */
    fun setHistoryInsightOutputLanguage(code: String) {
        _uiState.update { it.copy(historyInsightOutputLanguage = code) }
    }

    /**
     * Generate an AI insight copy of this session.
     *
     * Creates a new session with the same content and a batch insight produced by
     * [GenerateHistoryInsightUseCase]. Progress is streamed via [uiState].
     *
     * @param newSessionName Name to assign to the new copy session (localized by the caller).
     * @param onNavigateToSession Callback invoked with the new session ID upon success.
     */
    fun generateHistoryInsight(
        newSessionName: String?,
        modelNotDownloadedError: String,
        transcriptTooShortError: String,
        onNavigateToSession: (String) -> Unit
    ) {
        _uiState.update {
            it.copy(
                showHistoryInsightConfirm = false,
                isGeneratingHistoryInsight = true,
                historyInsightProgress = BatchInsightProgress.Idle
            )
        }

        historyInsightJob = viewModelScope.launch {
            // Protect inference from being killed when app is backgrounded.
            llmProcessingServiceController.startProcessing()
            try {
                // Signal that the LLM is being loaded — blocks all TopAppBar actions in the UI.
                _uiState.update { it.copy(isInitializingLlm = true) }

                // Ensure the LLM repository has a model path set before the batch pipeline runs.
                // When the user arrives at session details without going through the recording screen,
                // initialize() was never called and reloadModel() inside GenerateBatchInsightUseCase
                // would fail with "reloadModel called before initialize".
                // loadImmediately = false defers actual model loading to the first reloadModel() call.
                val settings = settingsRepository.getSettings().first()
                if (settings.llmModelPath.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isInitializingLlm = false,
                            isGeneratingHistoryInsight = false,
                            error = modelNotDownloadedError
                        )
                    }
                    return@launch
                }
                // Apply conservative threads before loading if the device was previously
                // detected as constrained (cached in DataStore across sessions).
                if (settings.memoryConstrainedDetected) {
                    Log.d(TAG, "History insight: applying conservative threads (memoryConstrainedDetected=true from DataStore)")
                    llmRepository.useConservativeThreads()
                }
                // Post-load check and per-chunk learning are handled inside
                // GenerateBatchInsightUseCase (checkAndCacheMemoryConstraint + recordConstrainedInference).
                initializeLlmUseCase(settings.llmModelPath, loadImmediately = false)
                    .onFailure { e ->
                        // Non-fatal report: LLM init silently failing here is the most common
                        // hidden cause of "history insight stuck / didn't work" complaints.
                        // Capture the cause + model path so we can correlate with model variants
                        // and OOM patterns in Crashlytics.
                        crashReporter.recordNonFatal(
                            e,
                            "history_insight: initializeLlmUseCase failed (modelPath=${settings.llmModelPath})"
                        )
                        _uiState.update {
                            it.copy(
                                isInitializingLlm = false,
                                isGeneratingHistoryInsight = false,
                                error = e.message ?: "Failed to initialize LLM"
                            )
                        }
                        return@launch
                    }

                // LLM initialized — pipeline is now running; clear the init flag.
                _uiState.update { it.copy(isInitializingLlm = false) }

                val outputLanguage = _uiState.value.historyInsightOutputLanguage
                val result = generateHistoryInsightUseCase(
                    sourceSessionId = sessionId,
                    newSessionName = newSessionName,
                    overrideOutputLanguage = outputLanguage.ifBlank { null },
                    onProgress = { progress ->
                        _uiState.update { it.copy(historyInsightProgress = progress) }
                    },
                    onSessionCreated = { newId ->
                        pendingHistorySessionId = newId
                    },
                    onChunkInsight = { chunkInsight ->
                        _uiState.update { state ->
                            state.copy(
                                intermediateHistoryInsights = state.intermediateHistoryInsights + chunkInsight
                            )
                        }
                    },
                    onFinalInsight = { finalInsight ->
                        _uiState.update { it.copy(finalHistoryInsight = finalInsight) }
                    }
                )

                // Release LLM from RAM — it is no longer needed after the pipeline completes.
                try {
                    llmRepository.cleanup()
                } catch (e: Exception) {
                    android.util.Log.e("SessionDetailsVM", "Error releasing LLM after history insight", e)
                }

                pendingHistorySessionId = null
                historyInsightJob = null

                result.onSuccess { newSessionId ->
                    if (newSessionId.isNotBlank()) {
                        _uiState.update {
                            it.copy(
                                isGeneratingHistoryInsight = false,
                                intermediateHistoryInsights = emptyList(),
                                finalHistoryInsight = null
                            )
                        }
                        onNavigateToSession(newSessionId)
                    } else {
                        // Blank session ID = use case skipped because transcript was too short.
                        // Surface a clear message instead of silently doing nothing — users were
                        // tapping "Create AI Copy" and seeing nothing happen.
                        _uiState.update {
                            it.copy(
                                isGeneratingHistoryInsight = false,
                                intermediateHistoryInsights = emptyList(),
                                finalHistoryInsight = null,
                                error = transcriptTooShortError
                            )
                        }
                    }
                }.onFailure { e ->
                    // CancellationException is handled by cancelHistoryInsight(); suppress here.
                    if (e !is CancellationException) {
                        crashReporter.recordNonFatal(e, "history_insight: pipeline failure")
                        _uiState.update {
                            it.copy(
                                isGeneratingHistoryInsight = false,
                                intermediateHistoryInsights = emptyList(),
                                finalHistoryInsight = null,
                                error = e.message ?: "Failed to generate insight"
                            )
                        }
                    }
                }
            } finally {
                // Always release the foreground token and clear init flag, even on cancellation.
                _uiState.update { it.copy(isInitializingLlm = false) }
                llmProcessingServiceController.stopProcessing()
            }
        }
    }

    /**
     * Cancel an in-progress history insight generation.
     *
     * Cancels the pipeline coroutine and deletes the partial copy session that was
     * created before the cancellation, leaving the original session untouched.
     */
    fun cancelHistoryInsight() {
        historyInsightJob?.cancel()
        historyInsightJob = null

        val partialId = pendingHistorySessionId
        pendingHistorySessionId = null

        viewModelScope.launch {
            // Delete the partial copy session so it does not appear in history.
            if (partialId != null) {
                deleteSessionUseCase(partialId)
                    .onFailure { e ->
                        android.util.Log.e("SessionDetailsVM", "Failed to delete partial session on cancel: ${e.message}")
                    }
            }
            // Release the LLM if it was already loaded during the cancelled pipeline.
            try {
                llmRepository.cleanup()
            } catch (e: Exception) {
                android.util.Log.e("SessionDetailsVM", "Error releasing LLM on cancel", e)
            }
            _uiState.update {
                it.copy(
                    isInitializingLlm = false,
                    isGeneratingHistoryInsight = false,
                    historyInsightProgress = BatchInsightProgress.Idle,
                    intermediateHistoryInsights = emptyList(),
                    finalHistoryInsight = null
                )
            }
        }
    }

    /**
     * Re-run AI analysis for a specific insight in place.
     *
     * Uses the insight's [sourceSegmentIds] to reconstruct the transcript text for that
     * chunk, then calls [GenerateFinalInsightUseCase] to produce a new insight and updates
     * the existing insight's title/content/tasks without creating a new session.
     *
     * If [sourceSegmentIds] is empty (the insight is a final/overall summary), the full
     * session transcript is used as the source text.
     *
     * @param insightId ID of the insight to replace.
     * @param modelNotDownloadedError Localized error string shown when LLM is not downloaded.
     */
    /**
     * Re-run AI analysis for a specific insight in-place.
     *
     * Delegates to [RegenerateInsightUseCase] — the single shared implementation used by
     * both this screen and [com.meetmind.assistant.presentation.main.MainViewModel].
     * All prompt construction, inference, and persistence run through the same code path.
     */
    fun regenerateInsight(insightId: String, modelNotDownloadedError: String) {
        val details = _uiState.value.sessionDetails ?: return
        val insight = details.insights.firstOrNull { it.id == insightId } ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(regeneratingInsightId = insightId, isInitializingLlm = true) }
            llmProcessingServiceController.startProcessing()
            try {
                val result = regenerateInsightUseCase(
                    insight = insight,
                    sessionId = sessionId,
                    mode = details.session.mode,
                    outputLanguage = details.session.outputLanguage,
                    topic = details.session.topic,
                    allSegments = details.segments
                )

                _uiState.update { it.copy(isInitializingLlm = false) }

                when (result) {
                    is RegenerateInsightUseCase.RegenerateResult.Success -> { /* DB Flow updates UI */ }
                    is RegenerateInsightUseCase.RegenerateResult.ModelNotDownloaded ->
                        _uiState.update { it.copy(error = modelNotDownloadedError) }
                    is RegenerateInsightUseCase.RegenerateResult.InitError ->
                        _uiState.update {
                            it.copy(error = result.cause.message ?: "Failed to initialize LLM")
                        }
                    is RegenerateInsightUseCase.RegenerateResult.EmptySource ->
                        _uiState.update { it.copy(error = modelNotDownloadedError) }
                    is RegenerateInsightUseCase.RegenerateResult.EmptyResponse -> { /* no-op */ }
                    is RegenerateInsightUseCase.RegenerateResult.Error ->
                        _uiState.update {
                            it.copy(error = result.cause.message ?: "Failed to regenerate insight")
                        }
                }
            } finally {
                _uiState.update { it.copy(regeneratingInsightId = null, isInitializingLlm = false) }
                llmProcessingServiceController.stopProcessing()
            }
        }
    }

    /**
     * Format timestamp for the markdown export header.
     *
     * Uses Locale.US for stable month abbreviations across device locales — exported
     * markdown often gets pasted into other tools that match on date strings, and
     * locale-specific month names ("thg 4" vs "Apr") break that matching.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.US)
        return dateFormat.format(java.util.Date(timestamp))
    }
}
