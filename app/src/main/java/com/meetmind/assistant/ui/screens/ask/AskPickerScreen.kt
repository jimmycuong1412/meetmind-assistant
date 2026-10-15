package com.meetmind.assistant.ui.screens.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.domain.model.TranscriptionSession
import com.meetmind.assistant.presentation.ask.AskPickerViewModel
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.icons.AppIcons
import com.meetmind.assistant.ui.ui.theme.recordingModeAccent
import com.meetmind.assistant.ui.ui.theme.semanticColors

/**
 * Pick the saved meetings an Ask MeetMind chat reads. Also used, with a `chatId`
 * nav argument, to change the meetings of an existing chat.
 *
 * @param onOpenChat Called with the chat id once the selection is saved.
 */
@Composable
fun AskPickerScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: AskPickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.openChat.collect { onOpenChat(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AskTopBar(
                title = stringResource(if (uiState.isEditing) R.string.ask_pick_edit_title else R.string.ask_pick_title),
                onNavigateBack = onNavigateBack,
                subtitle = {
                    Text(
                        text = stringResource(R.string.ask_pick_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.semanticColors.onGradientVariant
                    )
                },
                actions = {
                    if (!uiState.isEditing) {
                        IconButton(onClick = onOpenHistory) {
                            Icon(AppIcons.History, contentDescription = stringResource(R.string.ask_history_title))
                        }
                    }
                }
            )
        },
        bottomBar = {
            AskPickerFooter(
                selectedCount = uiState.selectedIds.size,
                selectedDurationMs = uiState.selectedDurationMs,
                isLongSelection = uiState.isLongSelection,
                isEditing = uiState.isEditing,
                canConfirm = uiState.canAsk,
                onClear = viewModel::clearSelection,
                onConfirm = viewModel::confirm
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val selected = uiState.selectedIds.toSet()
            val (recent, earlier) = remember(uiState.sessions) {
                uiState.sessions.partition { isInCurrentWeek(it.createdAt) }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.ask_pick_search_hint)) },
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }

                when {
                    uiState.isLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.sessions.isEmpty() -> item(key = "empty") {
                        Text(
                            text = stringResource(
                                if (uiState.query.isBlank()) R.string.ask_pick_empty else R.string.ask_pick_no_match
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        )
                    }
                    else -> {
                        if (recent.isNotEmpty()) {
                            item(key = "header-recent") { AskSectionHeader(stringResource(R.string.ask_section_this_week)) }
                            items(recent, key = { it.id }) { session ->
                                AskPickerRow(session, session.id in selected) { viewModel.toggle(session.id) }
                            }
                        }
                        if (earlier.isNotEmpty()) {
                            item(key = "header-earlier") { AskSectionHeader(stringResource(R.string.ask_section_earlier)) }
                            items(earlier, key = { it.id }) { session ->
                                AskPickerRow(session, session.id in selected) { viewModel.toggle(session.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AskSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun AskPickerRow(session: TranscriptionSession, selected: Boolean, onToggle: () -> Unit) {
    val accent = recordingModeAccent(session.mode)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // The whole row is the toggle target, so the checkbox takes no clicks itself
            // and TalkBack announces one checkable item.
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = askMeetingTitle(session.name, session.createdAt),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${askRelativeDate(session.createdAt)} · ${formatAskDuration(session.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = askModeLabel(session.mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AskPickerFooter(
    selectedCount: Int,
    selectedDurationMs: Long,
    isLongSelection: Boolean,
    isEditing: Boolean,
    canConfirm: Boolean,
    onClear: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pluralStringResource(R.plurals.ask_meetings_selected, selectedCount, selectedCount),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (selectedCount == 0) stringResource(R.string.ask_pick_select_hint)
                            else stringResource(R.string.ask_pick_selected_duration, formatAskDuration(selectedDurationMs)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedCount > 0) {
                        TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                    }
                }
                if (isLongSelection) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        AskInlineNote(
                            text = stringResource(R.string.ask_pick_long_selection),
                            color = MaterialTheme.semanticColors.warning,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.semanticColors.warning,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Button(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Icon(AppIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isEditing) R.string.ask_pick_confirm_edit else R.string.ask_pick_confirm))
                }
                AskInlineNote(
                    text = stringResource(R.string.ask_privacy_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
