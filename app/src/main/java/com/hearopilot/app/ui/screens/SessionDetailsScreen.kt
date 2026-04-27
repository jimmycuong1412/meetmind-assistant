package com.hearopilot.app.ui.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.hearopilot.app.ui.ui.theme.BrandPrimary
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.ui.theme.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.hearopilot.app.domain.model.ActionItem
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.SessionWithDetails
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.presentation.sessiondetails.SessionDetailsViewModel
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.ui.components.RenameSessionDialog
import com.hearopilot.app.ui.components.ShimmerInsightCard
import com.hearopilot.app.ui.components.ShimmerSessionDetails
import java.text.SimpleDateFormat
import java.util.*

/**
 * Session details screen — shows complete session information.
 *
 * Two tabs:
 *  - Transcript: editable transcription segments
 *  - AI Insights: insights with summary, tasks, source segments; streaming during generation
 *
 * @param onNavigateBack Navigate back to sessions list
 * @param onNavigateToSession Navigate to a different session by ID (used after insight copy is created)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    viewModel: SessionDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(modifier = Modifier.background(BrandPurpleDark)) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.session_details),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            enabled = !uiState.isGeneratingHistoryInsight
                        ) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        // Block all actions while the LLM model is loading to prevent CPU overload.
                        val actionsBlocked = uiState.isInitializingLlm || uiState.isGeneratingHistoryInsight
                        // While the LLM is loading, show a spinner in place of the AutoAwesome icon.
                        if (uiState.isInitializingLlm) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            }
                        } else {
                            // Pulse animation — active only while the coachmark is visible.
                            val infiniteTransition = rememberInfiniteTransition(label = "coachmark_pulse")
                            val pulseScale by if (uiState.showHistoryInsightCoachmark) {
                                infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.25f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(700, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulse_scale"
                                )
                            } else {
                                // Static value when coachmark is dismissed — no animation overhead.
                                remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
                            }

                            // TooltipBox anchors the coachmark visually to the icon with a pointer.
                            val tooltipState = rememberTooltipState(isPersistent = true)
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
                                tooltip = {
                                    RichTooltip(
                                        action = {
                                            TextButton(onClick = {
                                                tooltipState.dismiss()
                                                viewModel.dismissHistoryInsightCoachmark()
                                            }) {
                                                Text(stringResource(R.string.dismiss))
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.coachmark_history_insight))
                                    }
                                },
                                state = tooltipState
                            ) {
                                IconButton(
                                    onClick = { viewModel.showHistoryInsightConfirm() },
                                    enabled = !actionsBlocked
                                ) {
                                    Icon(
                                        imageVector = AppIcons.AutoAwesome,
                                        contentDescription = stringResource(R.string.generate_history_insight),
                                        tint = Color.White,
                                        modifier = Modifier.scale(pulseScale)
                                    )
                                }
                            }
                            // Show the coachmark on the first ever visit to this screen.
                            if (uiState.showHistoryInsightCoachmark) {
                                LaunchedEffect(Unit) {
                                    tooltipState.show()
                                }
                            }
                        }
                        // Share dropdown — Transcript or Summary
                        Box {
                            var showShareMenu by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { showShareMenu = true },
                                enabled = !actionsBlocked
                            ) {
                                Icon(
                                    imageVector = AppIcons.Share,
                                    contentDescription = stringResource(R.string.export),
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showShareMenu,
                                onDismissRequest = { showShareMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_transcript)) },
                                    onClick = {
                                        showShareMenu = false
                                        val textContent = viewModel.exportAsText()
                                        val exportTitle = context.getString(R.string.export_transcription)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, textContent)
                                            putExtra(Intent.EXTRA_TITLE, exportTitle)
                                        }
                                        context.startActivity(Intent.createChooser(intent, exportTitle))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_summary)) },
                                    onClick = {
                                        showShareMenu = false
                                        val summary = viewModel.exportAsSummaryMarkdown()
                                        val exportTitle = context.getString(R.string.share_summary)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, summary)
                                            putExtra(Intent.EXTRA_TITLE, exportTitle)
                                        }
                                        context.startActivity(Intent.createChooser(intent, exportTitle))
                                    }
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.showDeleteConfirmation() },
                            enabled = !actionsBlocked
                        ) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
            when {
                uiState.isLoading -> {
                    ShimmerSessionDetails(modifier = Modifier.padding(16.dp))
                }
                uiState.error != null -> {
                    ErrorMessage(
                        message = uiState.error!!,
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.sessionDetails != null -> {
                    SessionDetailsContent(
                        details = uiState.sessionDetails!!,
                        highlightId = uiState.highlightId,
                        initialTab = uiState.initialTab,
                        isGeneratingHistoryInsight = uiState.isGeneratingHistoryInsight,
                        historyInsightProgress = uiState.historyInsightProgress,
                        intermediateHistoryInsights = uiState.intermediateHistoryInsights,
                        finalHistoryInsight = uiState.finalHistoryInsight,
                        actionItems = uiState.actionItems,
                        onCancelGeneration = { viewModel.cancelHistoryInsight() },
                        onEditSegment = { viewModel.showEditSegmentDialog(it) },
                        onAssignSpeaker = { viewModel.showSpeakerAssignment(it.id) },
                        onEditInsight = { viewModel.showEditInsightFullDialog(it) },
                        onRegenerate = { insight ->
                            viewModel.regenerateInsight(
                                insight.id,
                                context.getString(R.string.llm_model_not_downloaded_error)
                            )
                        },
                        regeneratingInsightId = uiState.regeneratingInsightId,
                        onRename = { viewModel.showRenameDialog() },
                        onToggleTask = { id, done -> viewModel.toggleActionItem(id, done) },
                        onDeleteTask = { viewModel.deleteActionItem(it) }
                    )
                }
            }
        }

        // Rename dialog
        if (uiState.showRenameDialog) {
            RenameSessionDialog(
                currentName = uiState.sessionDetails?.session?.name,
                onDismiss = { viewModel.hideRenameDialog() },
                onConfirm = { newName -> viewModel.renameSession(newName) }
            )
        }

        // Edit segment dialog
        uiState.editingSegment?.let { segment ->
            EditTextDialog(
                title = stringResource(R.string.edit_transcription_title),
                initialText = segment.text,
                onDismiss = { viewModel.hideEditSegmentDialog() },
                onConfirm = { newText -> viewModel.saveSegmentText(segment.id, newText) }
            )
        }

        // Unified insight edit dialog — handles title, content, and tasks in one dialog.
        uiState.editingInsightFull?.let { insight ->
            EditInsightFullDialog(
                insight = insight,
                onDismiss = { viewModel.hideEditInsightFullDialog() },
                onConfirm = { newTitle, patchedContent, tasksText ->
                    viewModel.saveInsightFull(insight.id, newTitle, patchedContent, tasksText)
                }
            )
        }

        // Delete confirmation dialog
        if (uiState.showDeleteConfirmation) {
            DeleteConfirmationDialog(
                sessionName = uiState.sessionDetails?.session?.name,
                onDismiss = { viewModel.hideDeleteConfirmation() },
                onConfirm = {
                    viewModel.deleteSession { onNavigateBack() }
                }
            )
        }

        // Speaker assignment bottom sheet
        uiState.speakerAssignmentSegmentId?.let { segId ->
            // Find the segment to show its current speaker
            val currentSpeaker = uiState.sessionDetails?.segments
                ?.firstOrNull { it.id == segId }?.speaker
            SpeakerAssignmentSheet(
                currentSpeaker = currentSpeaker,
                onDismiss = { viewModel.hideSpeakerAssignment() },
                onSelect = { speaker -> viewModel.setSpeaker(segId, speaker) }
            )
        }

        // Confirm before starting history insight generation
        if (uiState.showHistoryInsightConfirm) {
            val originalName = uiState.sessionDetails?.session?.name
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val copySuffix = stringResource(R.string.history_insight_copy_suffix_format, timestamp)
            val newSessionName = if (!originalName.isNullOrBlank()) "$originalName $copySuffix" else null
            val modelNotDownloadedError = stringResource(R.string.llm_model_not_downloaded_error)

            HistoryInsightConfirmDialog(
                outputLanguage = uiState.historyInsightOutputLanguage,
                onOutputLanguageChange = viewModel::setHistoryInsightOutputLanguage,
                onDismiss = { viewModel.hideHistoryInsightConfirm() },
                onConfirm = {
                    viewModel.generateHistoryInsight(newSessionName, modelNotDownloadedError, onNavigateToSession)
                }
            )
        }
    }
}

// ─── Main content ────────────────────────────────────────────────────────────

/**
 * Two-tab layout: Transcript (segments only) and AI Insights (insights + progress).
 */
@Composable
private fun SessionDetailsContent(
    details: SessionWithDetails,
    highlightId: String?,
    initialTab: Int,
    isGeneratingHistoryInsight: Boolean,
    historyInsightProgress: BatchInsightProgress,
    intermediateHistoryInsights: List<LlmInsight>,
    finalHistoryInsight: LlmInsight?,
    actionItems: List<ActionItem>,
    onCancelGeneration: () -> Unit,
    onEditSegment: (TranscriptionSegment) -> Unit,
    onAssignSpeaker: (TranscriptionSegment) -> Unit,
    onEditInsight: (LlmInsight) -> Unit,
    onRegenerate: ((LlmInsight) -> Unit)? = null,
    regeneratingInsightId: String? = null,
    onRename: () -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    // Auto-switch to the AI Insights tab as soon as generation starts.
    LaunchedEffect(isGeneratingHistoryInsight) {
        if (isGeneratingHistoryInsight) selectedTab = 1
    }
    val pendingCount = actionItems.count { !it.isDone }
    val tabTitles = listOf(
        stringResource(R.string.tab_transcript),
        stringResource(R.string.tab_insights),
        if (pendingCount > 0) stringResource(R.string.tab_tasks_badge, pendingCount)
        else stringResource(R.string.tab_tasks)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Hero header
        HeroHeader(details = details, onRename = onRename)

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> TranscriptTab(
                segments = details.segments,
                highlightId = highlightId,
                onEditSegment = onEditSegment,
                onAssignSpeaker = onAssignSpeaker
            )
            1 -> AiInsightsTab(
                dbInsights = details.insights,
                segments = details.segments,
                insightStrategy = details.session.insightStrategy,
                isGenerating = isGeneratingHistoryInsight,
                progress = historyInsightProgress,
                intermediateInsights = intermediateHistoryInsights,
                finalInsight = finalHistoryInsight,
                highlightId = highlightId,
                onCancel = onCancelGeneration,
                onEditInsight = onEditInsight,
                onRegenerate = onRegenerate,
                regeneratingInsightId = regeneratingInsightId
            )
            2 -> TasksTab(
                actionItems = actionItems,
                onToggle = onToggleTask,
                onDelete = onDeleteTask
            )
        }
    }
}

// ─── Hero header ─────────────────────────────────────────────────────────────

@Composable
private fun HeroHeader(details: SessionWithDetails, onRename: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandPurpleDark)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Tapping the session name opens the rename dialog directly.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onRename() }
                        .padding(end = 4.dp)
                ) {
                    Text(
                        text = details.session.name ?: stringResource(R.string.session_unnamed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = AppIcons.Edit,
                        contentDescription = stringResource(R.string.rename),
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = formatTimestamp(details.session.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SessionStatChip(
                        icon = AppIcons.Mic,
                        label = "${details.completeSegmentCount} ${stringResource(R.string.segments_count)}"
                    )
                    SessionStatChip(
                        icon = AppIcons.AutoAwesome,
                        label = "${details.insights.size} ${stringResource(R.string.insights_count)}"
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

// ─── Transcript tab ───────────────────────────────────────────────────────────

/**
 * Displays only transcript segments, each editable.
 * Scrolls to the highlighted segment on first load (e.g. from search navigation).
 */
@Composable
private fun TranscriptTab(
    segments: List<TranscriptionSegment>,
    highlightId: String?,
    onEditSegment: (TranscriptionSegment) -> Unit,
    onAssignSpeaker: (TranscriptionSegment) -> Unit
) {
    val sortedSegments = remember(segments) { segments.sortedBy { it.timestamp } }
    val listState = rememberLazyListState()

    LaunchedEffect(highlightId, sortedSegments) {
        if (highlightId != null && sortedSegments.isNotEmpty()) {
            val index = sortedSegments.indexOfFirst { it.id == highlightId }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    if (sortedSegments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = stringResource(R.string.transcription_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sortedSegments, key = { it.id }) { segment ->
                TranscriptionSegmentCard(
                    segment = segment,
                    onEdit = { onEditSegment(segment) },
                    onAssignSpeaker = { onAssignSpeaker(segment) }
                )
            }
        }
    }
}

// ─── AI Insights tab ─────────────────────────────────────────────────────────

/**
 * Shows AI insights for a session.
 *
 * Static mode (not generating): displays DB insights with the final insight (empty sourceSegmentIds)
 * pinned at the top in a distinct color, and intermediate insights below.
 *
 * Generation mode: shows an inline progress banner, intermediate chunk insights as they complete,
 * and the final merged insight pinned at top when available. A shimmer card indicates the
 * in-flight chunk.
 */
@Composable
private fun AiInsightsTab(
    dbInsights: List<LlmInsight>,
    segments: List<TranscriptionSegment>,
    insightStrategy: com.hearopilot.app.domain.model.InsightStrategy,
    isGenerating: Boolean,
    progress: BatchInsightProgress,
    intermediateInsights: List<LlmInsight>,
    finalInsight: LlmInsight?,
    highlightId: String?,
    onCancel: () -> Unit,
    onEditInsight: (LlmInsight) -> Unit,
    onRegenerate: ((LlmInsight) -> Unit)? = null,
    regeneratingInsightId: String? = null
) {
    if (isGenerating) {
        GeneratingInsightsView(
            progress = progress,
            intermediateInsights = intermediateInsights,
            finalInsight = finalInsight,
            segments = segments,
            onCancel = onCancel
        )
    } else {
        StaticInsightsView(
            dbInsights = dbInsights,
            segments = segments,
            insightStrategy = insightStrategy,
            highlightId = highlightId,
            onEditInsight = onEditInsight,
            onRegenerate = onRegenerate,
            regeneratingInsightId = regeneratingInsightId
        )
    }
}

/**
 * Shows DB insights. Final insight (sourceSegmentIds.isEmpty()) pinned at top with accent color.
 */
@Composable
private fun StaticInsightsView(
    dbInsights: List<LlmInsight>,
    segments: List<TranscriptionSegment>,
    insightStrategy: com.hearopilot.app.domain.model.InsightStrategy,
    highlightId: String?,
    onEditInsight: (LlmInsight) -> Unit,
    onRegenerate: ((LlmInsight) -> Unit)? = null,
    regeneratingInsightId: String? = null
) {
    if (dbInsights.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = AppIcons.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = stringResource(R.string.insights_empty_history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.insights_empty_history_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val sortedInsights = remember(dbInsights) { dbInsights.sortedBy { it.timestamp } }

    // For END_OF_SESSION sessions, insights with no sourceSegmentIds are the overall final summary.
    // For REAL_TIME sessions every insight is a standalone snapshot — none is "overall".
    val isBatchSession = insightStrategy == com.hearopilot.app.domain.model.InsightStrategy.END_OF_SESSION
    val finalInsights = remember(sortedInsights, isBatchSession) {
        if (isBatchSession) sortedInsights.filter { it.sourceSegmentIds.isEmpty() } else emptyList()
    }
    val intermediateInsights = remember(sortedInsights, isBatchSession) {
        if (isBatchSession) sortedInsights.filter { it.sourceSegmentIds.isNotEmpty() } else sortedInsights
    }

    val listState = rememberLazyListState()
    LaunchedEffect(highlightId, finalInsights, intermediateInsights) {
        if (highlightId != null) {
            val allInsights = finalInsights + intermediateInsights
            val index = allInsights.indexOfFirst { it.id == highlightId }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Final (overall summary) insights pinned at top — only for END_OF_SESSION sessions.
        items(finalInsights, key = { it.id }) { insight ->
            InsightCard(
                insight = insight,
                segments = segments,
                isFinal = true,
                onEditInsight = { onEditInsight(insight) },
                onRegenerate = onRegenerate?.let { { it(insight) } },
                isRegenerating = regeneratingInsightId == insight.id
            )
        }
        // Intermediate (chunk) insights or all real-time insights below.
        items(intermediateInsights, key = { it.id }) { insight ->
            InsightCard(
                insight = insight,
                segments = segments,
                isFinal = false,
                onEditInsight = { onEditInsight(insight) },
                onRegenerate = onRegenerate?.let { { it(insight) } },
                isRegenerating = regeneratingInsightId == insight.id
            )
        }
    }
}

/**
 * Shows live generation progress and streaming insights.
 * The final merged insight is pinned at top as soon as it arrives.
 * A shimmer card at the bottom indicates the in-flight chunk.
 */
@Composable
private fun GeneratingInsightsView(
    progress: BatchInsightProgress,
    intermediateInsights: List<LlmInsight>,
    finalInsight: LlmInsight?,
    segments: List<TranscriptionSegment>,
    onCancel: () -> Unit
) {
    val isReducing = progress is BatchInsightProgress.Reducing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Progress banner
        item {
            GenerationProgressBanner(progress = progress, onCancel = onCancel)
        }

        // Final (merged) insight pinned at top as soon as it arrives
        finalInsight?.let { insight ->
            item(key = "final_${insight.id}") {
                InsightCard(
                    insight = insight,
                    segments = segments,
                    isFinal = true,
                    onEditInsight = null
                )
            }
        }

        // Intermediate chunk insights in chronological order
        items(intermediateInsights, key = { it.id }) { insight ->
            InsightCard(
                insight = insight,
                segments = segments,
                isFinal = false,
                onEditInsight = null
            )
        }

        // Shimmer for the currently in-flight chunk (not shown during reduce phase)
        if (!isReducing || finalInsight == null) {
            item { ShimmerInsightCard() }
        }
    }
}

/**
 * Inline progress banner shown at the top of the AI Insights tab during generation.
 */
@Composable
private fun GenerationProgressBanner(
    progress: BatchInsightProgress,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.generating_ai_insights),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Privacy notice — reuses existing onboarding string, already translated in all locales.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.onboarding_message_2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (progress) {
                is BatchInsightProgress.Mapping -> {
                    Text(
                        text = stringResource(
                            R.string.batch_processing_chunk,
                            progress.currentChunk,
                            progress.totalChunks
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { progress.currentChunk.toFloat() / progress.totalChunks },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is BatchInsightProgress.Reducing -> {
                    Text(
                        text = stringResource(R.string.batch_merging_insights),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is BatchInsightProgress.Error -> {
                    Text(
                        text = progress.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ─── Tasks tab ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TasksTab(
    actionItems: List<ActionItem>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    if (actionItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    AppIcons.TaskAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val pending = actionItems.filter { !it.isDone }
    val done = actionItems.filter { it.isDone }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (pending.isNotEmpty()) {
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text(
                        text = stringResource(R.string.tasks_pending, pending.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
            items(pending, key = { it.id }) { item ->
                TaskItem(item = item, onToggle = onToggle, onDelete = onDelete)
            }
        }
        if (done.isNotEmpty()) {
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text(
                        text = stringResource(R.string.tasks_completed, done.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 0.dp)
                            .padding(top = if (pending.isNotEmpty()) 12.dp else 4.dp)
                    )
                }
            }
            items(done, key = { it.id }) { item ->
                TaskItem(item = item, onToggle = onToggle, onDelete = onDelete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskItem(
    item: ActionItem,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item.id)
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = if (item.isDone)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface,
            tonalElevation = if (item.isDone) 0.dp else 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(item.id, !item.isDone) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (item.isDone) AppIcons.CheckBox else AppIcons.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (item.isDone) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isDone)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── Insight card ─────────────────────────────────────────────────────────────

/**
 * Card for a single LLM insight.
 *
 * @param isFinal When true, uses [primaryContainer] background and shows an "Overall Summary" badge.
 * @param onEditInsight Null during live generation (not yet persisted).
 * @param onEditTask Null during live generation.
 */
@Composable
private fun InsightCard(
    insight: LlmInsight,
    segments: List<TranscriptionSegment>,
    isFinal: Boolean,
    onEditInsight: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    isRegenerating: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(isFinal) }

    val cardColor = if (isFinal) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (isFinal) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = cardColor,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Icon container
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isFinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFinal) AppIcons.AutoAwesome else AppIcons.Lightbulb,
                                contentDescription = null,
                                tint = if (isFinal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // "Overall Summary" badge for final insights
                        if (isFinal) {
                            Text(
                                text = stringResource(R.string.final_insight_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = insight.title?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.insight_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy insight to clipboard
                    val clipboardManager = LocalClipboardManager.current
                    val actionItemsLabel = stringResource(R.string.action_items)
                    IconButton(onClick = {
                        val summary = parseInsightDisplayContent(insight.content)
                        val tasks = insight.tasks?.let { parseTasks(it) } ?: emptyList()
                        val sb = StringBuilder()
                        insight.title?.let { sb.appendLine(it).appendLine() }
                        sb.append(summary)
                        if (tasks.isNotEmpty()) {
                            sb.appendLine().appendLine()
                            sb.appendLine(actionItemsLabel)
                            tasks.forEach { sb.appendLine("• $it") }
                        }
                        clipboardManager.setText(AnnotatedString(sb.toString().trim()))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            AppIcons.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // Regenerate button — re-runs AI on source segments
                    if (onRegenerate != null) {
                        if (isRegenerating) {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    AppIcons.Refresh,
                                    contentDescription = stringResource(R.string.regenerate_insight),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    // Single edit button opens a unified dialog for title, content, and tasks.
                    if (onEditInsight != null) {
                        IconButton(onClick = onEditInsight, modifier = Modifier.size(32.dp)) {
                            Icon(
                                AppIcons.Edit,
                                contentDescription = stringResource(R.string.edit_item),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Collapsed preview
            if (!isExpanded) {
                Spacer(Modifier.height(8.dp))
                val preview = remember(insight.content) { parseInsightDisplayContent(insight.content) }
                Text(
                    text = preview.ifEmpty { stringResource(R.string.insight_data) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Expanded content
            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                FormattedInsightText(insight.content)

                // Tasks (read-only display; editing is done via the unified edit dialog)
                insight.tasks?.let { tasksJson ->
                    val tasks = remember(tasksJson) { parseTasks(tasksJson) }
                    if (tasks.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        TasksSection(tasks = tasks)
                    }
                }

                // Source segments (collapsible), only for intermediate insights
                if (insight.sourceSegmentIds.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SourceSegmentsSection(
                        sourceSegmentIds = insight.sourceSegmentIds,
                        segments = segments
                    )
                }
            }

            // Timestamp
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTimestamp(insight.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tasks section with edit support for each task item.
 */
@Composable
private fun TasksSection(tasks: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = AppIcons.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.action_items),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
            tasks.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "\u2022",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = task,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Collapsible source segments section within an insight card.
 */
@Composable
private fun SourceSegmentsSection(
    sourceSegmentIds: List<String>,
    segments: List<TranscriptionSegment>
) {
    val segmentMap = remember(segments) { segments.associateBy { it.id } }
    val sourceSegments = remember(sourceSegmentIds, segmentMap) {
        sourceSegmentIds.mapNotNull { segmentMap[it] }
    }

    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.source_transcription),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (sourceSegments.isNotEmpty()) {
                    Text(
                        text = sourceSegments.joinToString(" ") { it.text },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.segments_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Transcript segment card ──────────────────────────────────────────────────

@Composable
private fun TranscriptionSegmentCard(
    segment: TranscriptionSegment,
    onEdit: () -> Unit,
    onAssignSpeaker: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        val speakerLabel = segment.speaker
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left accent bar — purple if unassigned, speaker-color tinted if assigned
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        if (speakerLabel != null) speakerColor(speakerLabel).copy(alpha = 0.8f)
                        else BrandPrimary.copy(alpha = 0.45f)
                    )
            )
            Column(modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatTimestamp(segment.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Speaker chip — tap to change, shown when assigned
                        if (speakerLabel != null) {
                            SpeakerChip(
                                speaker = speakerLabel,
                                onClick = onAssignSpeaker
                            )
                        }
                    }
                    Row {
                        // Speaker assign button (person icon)
                        IconButton(onClick = onAssignSpeaker, modifier = Modifier.size(28.dp)) {
                            Icon(
                                AppIcons.Person,
                                contentDescription = stringResource(R.string.assign_speaker),
                                tint = if (speakerLabel != null) speakerColor(speakerLabel)
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(
                                AppIcons.Edit,
                                contentDescription = stringResource(R.string.edit_item),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Compact chip showing the speaker name. Tapping re-opens the assignment sheet. */
@Composable
private fun SpeakerChip(speaker: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = speakerColor(speaker).copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(20.dp)
    ) {
        Text(
            text = speaker,
            style = MaterialTheme.typography.labelSmall,
            color = speakerColor(speaker),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Returns a deterministic color for a speaker label. */
@Composable
private fun speakerColor(speaker: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF2196F3),  // blue
        Color(0xFF4CAF50),  // green
        Color(0xFFFF9800),  // orange
        Color(0xFF9C27B0),  // purple
        Color(0xFFF44336),  // red
    )
    val index = (speaker.hashCode() and 0x7FFFFFFF) % palette.size
    return palette[index]
}

/** Bottom-sheet dialog for assigning a speaker to a segment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeakerAssignmentSheet(
    currentSpeaker: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    val speakers = listOf(
        stringResource(R.string.speaker_me),
        stringResource(R.string.speaker_person_a),
        stringResource(R.string.speaker_person_b),
        stringResource(R.string.speaker_person_c),
        stringResource(R.string.speaker_person_d)
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.assign_speaker),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            speakers.forEach { speaker ->
                val isSelected = speaker == currentSpeaker
                ListItem(
                    headlineContent = { Text(speaker) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(speakerColor(speaker), RoundedCornerShape(8.dp))
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else Color.Transparent
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(speaker) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // Clear label option
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.speaker_clear),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(
                        AppIcons.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(null) }
            )
        }
    }
}

// ─── Task card (used from SessionsScreen as well) ─────────────────────────────

@Composable
fun TaskCard(task: String, onEdit: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(task, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        AppIcons.Edit,
                        contentDescription = stringResource(R.string.edit_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Unified insight edit dialog ─────────────────────────────────────────────

/**
 * Converts a tasks JSON array string into a newline-delimited plain-text string
 * for display and editing in a text field.
 */
private fun parseTasksToText(tasksJson: String?): String {
    if (tasksJson == null) return ""
    return try {
        val arr = org.json.JSONArray(tasksJson)
        (0 until arr.length()).joinToString("\n") { i ->
            when (val item = arr.get(i)) {
                is org.json.JSONObject -> item.optString("description", "")
                else -> item.toString()
            }
        }
    } catch (e: Exception) { "" }
}

/**
 * Unified dialog for editing an insight's title, summary content, and tasks in one place.
 *
 * The tasks field is only shown when the insight has existing tasks.
 * Each task occupies one line in the text field; empty lines are ignored on save.
 *
 * The caller is responsible for passing [patchedContent] (constructed via [patchInsightContent])
 * back to the ViewModel so the JSON structure of the content column is preserved.
 */
@Composable
private fun EditInsightFullDialog(
    insight: LlmInsight,
    onDismiss: () -> Unit,
    onConfirm: (title: String, patchedContent: String, tasksText: String) -> Unit
) {
    val initialTitle = insight.title ?: ""
    val initialSummary = remember(insight.content) { parseInsightDisplayContent(insight.content) }
    val initialTasksText = remember(insight.tasks) { parseTasksToText(insight.tasks) }

    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var summary by rememberSaveable { mutableStateOf(initialSummary) }
    var tasksText by rememberSaveable { mutableStateOf(initialTasksText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.edit_insight_title),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.insight_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.insight_data)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                if (initialTasksText.isNotEmpty()) {
                    OutlinedTextField(
                        value = tasksText,
                        onValueChange = { tasksText = it },
                        label = { Text(stringResource(R.string.action_items)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val patched = patchInsightContent(insight.content, summary)
                onConfirm(title, patched, tasksText)
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

/**
 * Confirmation dialog before starting history insight generation.
 * Clearly communicates that a copy will be created.
 */
@Composable
fun HistoryInsightConfirmDialog(
    outputLanguage: String,
    onOutputLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                stringResource(R.string.history_insight_confirm_title),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Copy notice banner
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = AppIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_insight_copy_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                LanguageSelector(
                    languages = SupportedLanguages.ALL,
                    selectedCode = outputLanguage,
                    onLanguageSelected = onOutputLanguageChange,
                    label = stringResource(R.string.new_session_output_language),
                    showAutoOption = true
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.create_ai_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun DeleteConfirmationDialog(
    sessionName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.delete_session_title), color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(
                text = if (sessionName != null) {
                    stringResource(R.string.delete_session_message, sessionName)
                } else {
                    stringResource(R.string.delete_session_message_unnamed)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ErrorMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDismiss) {
            Text(stringResource(R.string.dismiss))
        }
    }
}

/**
 * Generic single-field edit dialog used for segments, insights, and tasks.
 */
@Composable
private fun EditTextDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                placeholder = { Text(stringResource(R.string.edit_content_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

/**
 * Extracts human-readable summary from JSON insight content, or returns raw content if not JSON.
 */
private fun parseInsightDisplayContent(content: String): String {
    if (content.trim().startsWith("{")) {
        return try {
            val json = org.json.JSONObject(content)
            if (json.has("summary")) json.getString("summary") else content
        } catch (e: Exception) {
            content
        }
    }
    return content
}

/**
 * Patches only the "summary" field of JSON insight content; falls back to replacing the whole string.
 */
private fun patchInsightContent(originalContent: String, newSummary: String): String {
    if (originalContent.trim().startsWith("{")) {
        return try {
            val json = org.json.JSONObject(originalContent)
            if (json.has("summary")) {
                json.put("summary", newSummary)
                json.toString()
            } else {
                newSummary
            }
        } catch (e: Exception) {
            newSummary
        }
    }
    return newSummary
}

/**
 * Parses a JSON tasks array string into a list of task strings.
 */
private fun parseTasks(tasksJson: String): List<String> {
    return try {
        val array = org.json.JSONArray(tasksJson)
        List(array.length()) { i ->
            val item = array.get(i)
            when (item) {
                is org.json.JSONObject -> item.optString("description", "")
                else -> item.toString()
            }
        }.filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}
