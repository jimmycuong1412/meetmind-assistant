package com.meetmind.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.icons.AppIcons
import com.meetmind.assistant.ui.ui.theme.*
import com.meetmind.assistant.domain.model.TranscriptionSegment
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Transcription display.
 * - Follows the latest segment (including a growing partial) while the user is at the bottom
 * - Stops following once the user scrolls up, so earlier lines can be read without being
 *   pulled back down; a "latest" button (or scrolling back to the bottom) resumes following
 * - Minimal empty state when no segments yet (no hero, just centered icon + text)
 */
@Composable
fun TranscriptionSection(
    segments: List<TranscriptionSegment>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    // Index of the trailing anchor item. Scrolling to it lands at the very end of the list
    // (the list clamps), even when the last segment is taller than the viewport.
    val endIndex = segments.size
    // Start at the end so re-entering the tab doesn't animate down from the top.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = endIndex)
    val coroutineScope = rememberCoroutineScope()
    var followLatest by remember { mutableStateOf(true) }
    var lastSegmentCount by remember { mutableIntStateOf(segments.size) }

    // Only a user drag stops following. Programmatic scrolls can be cut short by the next
    // update, so they must not be mistaken for the user scrolling away.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions
            .filterIsInstance<DragInteraction.Start>()
            .collect { followLatest = false }
    }
    // Following resumes whenever a scroll settles at the bottom.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect { if (!listState.canScrollForward) followLatest = true }
    }

    // A partial segment grows in place without changing the count, so key on its text too.
    LaunchedEffect(segments.size, segments.lastOrNull()?.text) {
        val added = segments.size > lastSegmentCount
        lastSegmentCount = segments.size
        if (!followLatest || segments.isEmpty()) return@LaunchedEffect
        if (added) listState.animateScrollToItem(endIndex) else listState.scrollToItem(endIndex)
    }

    Box(modifier = modifier) {
        if (segments.isEmpty()) {
            TranscriptionEmptyState(
                isRecording = isRecording,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Bottom padding is reduced by the 12.dp gap placed before the end anchor.
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(segments) { segment ->
                    TranscriptionItem(segment)
                }
                item(key = "end_anchor") { Spacer(Modifier.height(0.dp)) }
            }

            AnimatedVisibility(
                visible = !followLatest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        followLatest = true
                        coroutineScope.launch { listState.animateScrollToItem(endIndex) }
                    }
                ) {
                    Icon(
                        imageVector = AppIcons.ExpandMore,
                        contentDescription = stringResource(R.string.transcription_jump_to_latest)
                    )
                }
            }
        }
    }
}

/**
 * Empty state: centered icon + text, no gradient hero.
 *
 * Idle:      static mic icon, "Press Record to start" hint.
 * Recording: mic icon pulses, text changes to "Listening…".
 */
@Composable
private fun TranscriptionEmptyState(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse animation — always created, only applied when recording.
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )
    val iconScale = if (isRecording) pulseScale else 1f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppIcons.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
            modifier = Modifier
                .size(56.dp)
                .scale(iconScale)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isRecording) stringResource(R.string.transcription_listening)
                   else stringResource(R.string.transcription_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        if (!isRecording) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.transcription_press_record),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

@Composable
private fun TranscriptionItem(segment: TranscriptionSegment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (segment.isComplete) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        },
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = segment.text,
                style = MaterialTheme.typography.transcription,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (segment.isComplete) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.semanticColors.success, modifier = Modifier.size(14.dp))
                    Text(text = stringResource(R.string.transcription_complete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.semanticColors.success, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
