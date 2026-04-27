package com.hearopilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.ui.components.GradientButton
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.usecase.llm.InterviewOutputParser
import com.hearopilot.app.ui.components.ShimmerInsightCard
import kotlinx.coroutines.launch

// ─── Scroll-proximity threshold ──────────────────────────────────────────────
// Auto-scroll only fires when the user is within this many items of the last
// visible item. This preserves usability: if someone scrolls up to re-read
// earlier insights, auto-scroll stays dormant until they return to the bottom.
private const val AUTO_SCROLL_PROXIMITY_ITEMS = 2

/**
 * Minimalist insights display
 * - Flat design with subtle accents
 * - Clear visual hierarchy
 * - Brand primary accent color
 * - Formatted text with markdown-like rendering
 *
 * In Interview Mode, a Live Sync control button floats over the list. When enabled
 * it auto-scrolls to the newest insight each time one arrives, so the coach panel
 * always shows the most recent answer suggestion without manual scrolling. Auto-scroll
 * is suppressed when the user has scrolled more than [AUTO_SCROLL_PROXIMITY_ITEMS]
 * away from the bottom so they can review earlier cards freely.
 */
@Composable
fun InsightsSection(
    insights: List<LlmInsight>,
    segments: List<TranscriptionSegment>,
    llmStatus: String,
    isLlmEnabled: Boolean = true,
    isLlmAvailable: Boolean = true,
    isFinalizingSession: Boolean = false,
    isRecording: Boolean = false,
    insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    batchProgress: BatchInsightProgress = BatchInsightProgress.Idle,
    recordingMode: RecordingMode = RecordingMode.SHORT_MEETING,
    onDownloadLlm: () -> Unit = {},
    onRegenerate: ((LlmInsight) -> Unit)? = null,
    regeneratingInsightId: String? = null,
    modifier: Modifier = Modifier
) {
    val isInterviewMode = recordingMode == RecordingMode.INTERVIEW

    // Live-sync is on by default only in Interview Mode so the coach panel tracks
    // the conversation automatically. For other modes the user can still enable it.
    var isLiveSyncEnabled by remember(isInterviewMode) { mutableStateOf(isInterviewMode) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // ─── Auto-scroll logic ────────────────────────────────────────────────────
    // Fires whenever the insights list grows (new insight arrived or shimmer added).
    // Only scrolls if live-sync is on AND the user is near the bottom of the list.
    val effectiveItemCount = insights.size + if (isFinalizingSession) 1 else 0
    LaunchedEffect(effectiveItemCount, isLiveSyncEnabled) {
        if (!isLiveSyncEnabled || effectiveItemCount == 0) return@LaunchedEffect
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = listState.layoutInfo.totalItemsCount
        val isNearBottom = (totalItems - 1 - lastVisibleIndex) <= AUTO_SCROLL_PROXIMITY_ITEMS
        if (isNearBottom || totalItems <= AUTO_SCROLL_PROXIMITY_ITEMS) {
            listState.animateScrollToItem(effectiveItemCount - 1)
        }
    }

    Box(modifier = modifier) {
        if (!isLlmEnabled) {
            // LLM disabled by the user in Settings — show informational message, not download prompt
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.insights_disabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.insights_disabled_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (!isLlmAvailable) {
            // LLM enabled but model not yet downloaded — prompt the user to download
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top spacer to push content toward center when space allows
                Spacer(modifier = Modifier.weight(1f, fill = false))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,

                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.insights_download_prompt),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        GradientButton(
                            text = stringResource(R.string.insights_download_button),
                            onClick = onDownloadLlm,
                            modifier = Modifier.fillMaxWidth(),
                            // Downloading the LLM while STT is active would load ~1 GB into RAM
                            // on top of the already-loaded STT model, causing OOM / freeze.
                            enabled = !isRecording
                        )
                    }
                }

                // Bottom spacer to push content toward center when space allows
                Spacer(modifier = Modifier.weight(1f, fill = false))
            }
        } else if (insightStrategy == InsightStrategy.END_OF_SESSION && isRecording && insights.isEmpty()) {
            // END_OF_SESSION mode: recording in progress — show info card.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Summarize,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.batch_insights_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Light,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (isFinalizingSession && batchProgress !is BatchInsightProgress.Idle && batchProgress !is BatchInsightProgress.Complete) {
            // Batch pipeline running — show progress UI.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (val progress = batchProgress) {
                        is BatchInsightProgress.Mapping -> {
                            Text(
                                text = stringResource(
                                    R.string.batch_processing_chunk,
                                    progress.currentChunk,
                                    progress.totalChunks
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress.currentChunk.toFloat() / progress.totalChunks },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is BatchInsightProgress.Reducing -> {
                            Text(
                                text = stringResource(R.string.batch_merging_insights),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is BatchInsightProgress.Error -> {
                            Icon(
                                imageVector = AppIcons.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = progress.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        else -> {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        } else if (insights.isEmpty() && isFinalizingSession) {
            // Session just ended: no prior insights but the final one is being generated.
            // Show a single shimmer card so the user sees immediate feedback.
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ShimmerInsightCard() }
            }
        } else if (insights.isEmpty()) {
            // Empty state (LLM available but no insights yet)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.insights_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Light,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.insights_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // ── Insights list ──────────────────────────────────────────────────
            // A shimmer card at the bottom signals the final insight is in flight.
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 8.dp,
                    // Extra bottom padding ensures the last card is not obscured by the
                    // floating sync button when live-sync is available.
                    bottom = if (isRecording || isFinalizingSession) 80.dp else 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(insights, key = { it.id }) { insight ->
                    InsightItem(
                        insight = insight,
                        segments = segments,
                        recordingMode = recordingMode,
                        onRegenerate = onRegenerate?.let { { it(insight) } },
                        isRegenerating = regeneratingInsightId == insight.id
                    )
                }
                if (isFinalizingSession) {
                    item { ShimmerInsightCard(modifier = Modifier.padding(horizontal = 0.dp)) }
                }
            }
        }

        // ── Live Sync control ──────────────────────────────────────────────────
        // Shown whenever there are insights and recording is active (or the session
        // is still finalizing). In Interview Mode it glows with the interview accent
        // color; in other modes it uses the primary brand color.
        val showSyncButton = (isRecording || isFinalizingSession) && insights.isNotEmpty()
        AnimatedVisibility(
            visible = showSyncButton,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            LiveSyncButton(
                isEnabled = isLiveSyncEnabled,
                isInterviewMode = isInterviewMode,
                onToggle = { enabled ->
                    isLiveSyncEnabled = enabled
                    // When the user re-enables sync, immediately jump to the latest item.
                    if (enabled && effectiveItemCount > 0) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(effectiveItemCount - 1)
                        }
                    }
                }
            )
        }
    }
}

// ─── Live Sync Button ─────────────────────────────────────────────────────────

/**
 * Floating pill button that toggles real-time auto-scroll of the insights list.
 *
 * - Enabled: solid gradient background, subtle breathing pulse animation, "Live" label
 * - Disabled: outlined style, "Sync off" label
 * - Interview mode: rose/pink gradient accent; other modes: brand-purple gradient
 */
@Composable
private fun LiveSyncButton(
    isEnabled: Boolean,
    isInterviewMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val activeGradient: Brush = if (isInterviewMode) {
        ModeInterviewGradient
    } else {
        PrimaryGradient
    }
    val activeTextColor = Color.White
    val inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Breathing pulse animation — only plays when enabled to signal live activity
    val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isEnabled) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sync_scale"
    )

    Box(
        modifier = Modifier
            .scale(pulseScale)
            .clip(RoundedCornerShape(50))
            .then(
                if (isEnabled) {
                    Modifier.background(activeGradient)
                } else {
                    Modifier
                        .background(inactiveContainerColor)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(50)
                        )
                }
            )
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isEnabled) {
                // Live indicator dot — solid circle
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            color = activeTextColor.copy(alpha = 0.9f),
                            shape = CircleShape
                        )
                )
            } else {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = null,
                    tint = inactiveContentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = if (isEnabled) stringResource(R.string.live_sync_on)
                       else stringResource(R.string.live_sync_off),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isEnabled) activeTextColor else inactiveContentColor
            )
        }
    }
}

// ─── InsightItem ──────────────────────────────────────────────────────────────

@Composable
private fun InsightItem(
    insight: LlmInsight,
    segments: List<TranscriptionSegment>,
    recordingMode: RecordingMode = RecordingMode.SHORT_MEETING,
    onRegenerate: (() -> Unit)? = null,
    isRegenerating: Boolean = false
) {
    val isInterviewMode = recordingMode == RecordingMode.INTERVIEW
    val isCoachingNote = isInterviewMode &&
            insight.title?.startsWith(InterviewOutputParser.COACHING_NOTE_PREFIX) == true

    if (isInterviewMode) {
        InterviewInsightItem(
            insight = insight,
            segments = segments,
            isCoachingNote = isCoachingNote,
            onRegenerate = onRegenerate,
            isRegenerating = isRegenerating
        )
    } else {
        StandardInsightItem(
            insight = insight,
            segments = segments,
            onRegenerate = onRegenerate,
            isRegenerating = isRegenerating
        )
    }
}

/**
 * Card for Interview Mode insights. Two visual variants driven by [isCoachingNote]:
 *
 * **Question detected** — rose accent, Psychology icon, detected question shown in a
 * badge chip above the answer suggestion, coaching tips in a secondary section.
 *
 * **Coaching note** — amber accent, Lightbulb icon, no question chip, general coaching
 * content shown directly, tips listed below.
 */
@Composable
private fun InterviewInsightItem(
    insight: LlmInsight,
    segments: List<TranscriptionSegment>,
    isCoachingNote: Boolean,
    onRegenerate: (() -> Unit)? = null,
    isRegenerating: Boolean = false
) {
    // ── Derived display values ────────────────────────────────────────────────
    // For coaching notes the title encodes the role as "coaching:<role>"; strip the prefix.
    // For question cards the title IS the detected question text.
    val accentColor = if (isCoachingNote) {
        Color(0xFFD97706) // amber-600 — coaching / lightbulb feel
    } else {
        ModeInterviewTint   // rose — question detected
    }
    val headerIcon = if (isCoachingNote) AppIcons.Lightbulb else AppIcons.Psychology
    val headerLabel = if (isCoachingNote) {
        stringResource(R.string.interview_coaching_note)
    } else {
        stringResource(R.string.interview_question_detected)
    }
    // For question cards, show the question text in a chip; for coaching notes, nothing extra.
    val detectedQuestion = if (!isCoachingNote) insight.title else null

    val segmentMap = remember(segments) { segments.associateBy { it.id } }
    val sourceSegments = remember(insight.sourceSegmentIds, segmentMap) {
        insight.sourceSegmentIds.mapNotNull { id -> segmentMap[id] }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Coloured accent badge housing the mode icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = headerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Regenerate button
                if (onRegenerate != null) {
                    if (isRegenerating) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = accentColor
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
            }

            // ── Detected question chip ─────────────────────────────────────
            // Shown only when a question was detected. Renders the question text in a
            // lightly tinted surface so it's visually distinct from the answer below.
            if (detectedQuestion != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = accentColor.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = detectedQuestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // ── Answer suggestion label + content ──────────────────────────
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.interview_answer_suggestion),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            FormattedInsightText(insight.content)

            // ── Coaching tips ──────────────────────────────────────────────
            insight.tasks?.let { tasksJson ->
                Spacer(modifier = Modifier.height(12.dp))
                CoachingTipsList(tasksJson, accentColor)
            }

            // ── Source transcription (collapsed) ───────────────────────────
            if (insight.sourceSegmentIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SourceTranscriptionSection(sourceSegments)
            }
        }
    }
}

/**
 * Standard insight card for all non-Interview modes. Unchanged from the original layout.
 */
@Composable
private fun StandardInsightItem(
    insight: LlmInsight,
    segments: List<TranscriptionSegment>,
    onRegenerate: (() -> Unit)? = null,
    isRegenerating: Boolean = false
) {
    val segmentMap = remember(segments) { segments.associateBy { it.id } }
    val sourceSegments = remember(insight.sourceSegmentIds, segmentMap) {
        insight.sourceSegmentIds.mapNotNull { id -> segmentMap[id] }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Insight indicator with title (if present)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = insight.title ?: stringResource(R.string.insight_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Formatted insight content (always visible)
            FormattedInsightText(insight.content)

            // Tasks (if present)
            insight.tasks?.let { tasksJson ->
                Spacer(modifier = Modifier.height(12.dp))
                TasksList(tasksJson)
            }

            // Source transcription — collapsed by default; tap the header to expand.
            if (insight.sourceSegmentIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SourceTranscriptionSection(sourceSegments)
            }
        }
    }
}

// ─── SourceTranscriptionSection ───────────────────────────────────────────────

/**
 * Collapsible panel that shows the raw transcript segments that fed this insight.
 * Shared by both [StandardInsightItem] and [InterviewInsightItem].
 */
@Composable
private fun SourceTranscriptionSection(sourceSegments: List<TranscriptionSegment>) {
    var sourceExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Clickable header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { sourceExpanded = !sourceExpanded },
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
                    imageVector = if (sourceExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = if (sourceExpanded)
                        stringResource(R.string.collapse)
                    else
                        stringResource(R.string.expand),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Collapsible content
            if (sourceExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (sourceSegments.isNotEmpty()) {
                    Text(
                        text = sourceSegments.joinToString(" ") { it.text },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                } else {
                    Text(
                        text = stringResource(R.string.segments_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

// ─── TasksList ────────────────────────────────────────────────────────────────

/** Parses a JSON array string and returns the non-blank items. Returns empty list on error. */
private fun parseTasksJson(tasksJson: String): List<String> = try {
    val arr = org.json.JSONArray(tasksJson)
    List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
} catch (e: Exception) {
    emptyList()
}

/**
 * Action-items section used by standard meeting/listening/translation insight cards.
 * Teal tertiary accent with checkmark icon.
 */
@Composable
private fun TasksList(tasksJson: String) {
    val tasks = remember(tasksJson) { parseTasksJson(tasksJson) }

    if (tasks.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = task,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Coaching-tips section used exclusively by [InterviewInsightItem].
 * Uses the same rose/amber [accentColor] as the card header so the tips feel
 * contextually linked to the interview mode rather than looking like action items.
 */
@Composable
private fun CoachingTipsList(tasksJson: String, accentColor: Color) {
    val tips = remember(tasksJson) { parseTasksJson(tasksJson) }

    if (tips.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = accentColor.copy(alpha = 0.07f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Lightbulb,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.interview_coaching_tips),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                tips.forEach { tip ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ─── FormattedInsightText ─────────────────────────────────────────────────────

/**
 * Renders insight text with full Markdown support using compose-richtext.
 * Supports headers, lists, bold, italic, code blocks, and more.
 */
@Composable
internal fun FormattedInsightText(content: String) {
    RichText(
        modifier = Modifier.fillMaxWidth()
    ) {
        Markdown(content)
    }
}
