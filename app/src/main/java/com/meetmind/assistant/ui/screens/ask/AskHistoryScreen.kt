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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.domain.model.AskChatSummary
import com.meetmind.assistant.presentation.ask.AskHistoryViewModel
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.icons.AppIcons
import com.meetmind.assistant.ui.ui.theme.semanticColors

/** Saved Ask MeetMind chats: open, rename (with undo), pin, delete, start a new one. */
@Composable
fun AskHistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    onNewChat: () -> Unit,
    viewModel: AskHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    val renamedText = stringResource(R.string.ask_renamed)
    val undoText = stringResource(R.string.ask_undo)
    LaunchedEffect(uiState.lastRename) {
        if (uiState.lastRename == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(renamedText, actionLabel = undoText, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRename() else viewModel.clearRenameUndo()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AskTopBar(title = stringResource(R.string.ask_history_title), onNavigateBack = onNavigateBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(AppIcons.AutoAwesome, contentDescription = null) },
                text = { Text(stringResource(R.string.ask_history_new_chat)) },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!uiState.isLoading && uiState.chats.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AskAssistantBadge(size = 48.dp)
                    Text(stringResource(R.string.ask_history_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.ask_history_empty_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                return@Box
            }

            val (recent, earlier) = remember(uiState.others) {
                uiState.others.partition { isInCurrentWeek(it.updatedAt) }
            }
            val sections = listOf(
                Triple("pinned", stringResource(R.string.ask_history_pinned), uiState.pinned),
                Triple("recent", stringResource(R.string.ask_section_this_week), recent),
                Triple("earlier", stringResource(R.string.ask_section_earlier), earlier)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .navigationBarsPadding(),
                // Bottom padding keeps the last card clear of the FAB.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { (key, title, chats) ->
                    if (chats.isEmpty()) return@forEach
                    item(key = "header-$key") { AskSectionHeader(title) }
                    items(chats, key = { it.id }) { chat ->
                        AskChatCard(
                            chat = chat,
                            onOpen = { onOpenChat(chat.id) },
                            onRename = { renaming = chat.id },
                            onTogglePin = { viewModel.togglePinned(chat.id) },
                            onDelete = { deleting = chat.id }
                        )
                    }
                }
                item(key = "note") {
                    AskInlineNote(
                        text = stringResource(R.string.ask_history_local_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    renaming?.let { chatId -> uiState.chats.firstOrNull { it.id == chatId } }?.let { chat ->
        AskRenameDialog(
            initial = chat.displayTitle.orEmpty(),
            onDismiss = { renaming = null },
            onSave = { title ->
                viewModel.rename(chat.id, title)
                renaming = null
            }
        )
    }

    deleting?.let { chatId -> uiState.chats.firstOrNull { it.id == chatId } }?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.ask_history_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ask_history_delete_message,
                        chat.displayTitle ?: stringResource(R.string.ask_untitled_chat)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(chat.id)
                    deleting = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun AskChatCard(
    chat: AskChatSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val title = chat.displayTitle ?: stringResource(R.string.ask_untitled_chat)
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                chat.lastAnswer?.let { answer ->
                    Text(
                        text = plainAnswerText(answer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (chat.missingSessionCount > 0) {
                    AskInlineNote(
                        text = stringResource(R.string.ask_history_missing, chat.missingSessionCount, chat.sessionCount),
                        color = MaterialTheme.semanticColors.warning
                    ) {
                        Icon(AppIcons.Info, contentDescription = null, tint = MaterialTheme.semanticColors.warning, modifier = Modifier.size(14.dp))
                    }
                } else {
                    val meetings = chat.singleSessionName?.takeIf { it.isNotBlank() }
                        ?: pluralStringResource(R.plurals.ask_history_meetings, chat.sessionCount, chat.sessionCount)
                    Text(
                        text = listOf(
                            meetings,
                            pluralStringResource(R.plurals.ask_history_questions, chat.questionCount, chat.questionCount),
                            askRelativeDate(chat.updatedAt)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.padding(top = 4.dp, end = 4.dp)) {
                    Icon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = stringResource(R.string.ask_history_options, title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (chat.pinned) R.string.ask_history_unpin else R.string.ask_history_pin)) },
                        leadingIcon = { Icon(AppIcons.PushPin, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ask_history_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AskRenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial.take(AskHistoryViewModel.MAX_TITLE_LENGTH)) }
    val empty = value.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ask_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.ask_rename_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(AskHistoryViewModel.MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.ask_rename_label)) },
                    singleLine = true,
                    isError = empty,
                    trailingIcon = {
                        if (value.isNotEmpty()) {
                            IconButton(onClick = { value = "" }) {
                                Icon(AppIcons.Cancel, contentDescription = stringResource(R.string.ask_rename_clear))
                            }
                        }
                    },
                    supportingText = {
                        Row {
                            Text(
                                text = stringResource(if (empty) R.string.ask_rename_empty else R.string.ask_rename_helper),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${value.length}/${AskHistoryViewModel.MAX_TITLE_LENGTH}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = !empty) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
