package com.meetmind.assistant.ui.screens.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.domain.model.AskCitation
import com.meetmind.assistant.domain.model.AskMessage
import com.meetmind.assistant.domain.model.AskRole
import com.meetmind.assistant.domain.model.TranscriptionSession
import com.meetmind.assistant.presentation.ask.AskChatViewModel
import com.meetmind.assistant.presentation.ask.AskError
import com.meetmind.assistant.presentation.ask.AskPending
import com.meetmind.assistant.presentation.ask.AskSourceState
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.icons.AppIcons
import com.meetmind.assistant.ui.ui.theme.semanticColors
import kotlinx.coroutines.launch

/**
 * One Ask MeetMind chat: questions about the chat's meetings, answers with tappable
 * citations, and a sheet showing the transcript behind each citation.
 */
@Composable
fun AskChatScreen(
    onNavigateBack: () -> Unit,
    onEditMeetings: (chatId: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSession: (sessionId: String, segmentId: String?) -> Unit,
    onDownloadModel: () -> Unit,
    viewModel: AskChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.ask_copied)
    val onEdit = { onEditMeetings(viewModel.chatId) }

    val meetingTitles = uiState.meetings.associate { it.id to askMeetingTitle(it.name, it.createdAt) }
    val showEmptyState = uiState.messages.isEmpty() && uiState.pending == null

    // Follow the conversation when a message, an answer phase or a retry row appears.
    // Not on every streamed token, so reading earlier answers is not interrupted.
    val rowCount = uiState.messages.size + (if (uiState.pending != null || uiState.unanswered) 1 else 0)
    LaunchedEffect(rowCount, uiState.pending?.phase) {
        if (rowCount > 0 && !showEmptyState) listState.animateScrollToItem(rowCount - 1)
    }

    fun send(question: String) {
        if (question.isBlank() || uiState.pending != null) return
        viewModel.ask(question)
        input = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AskTopBar(
                title = stringResource(R.string.ask_title),
                onNavigateBack = onNavigateBack,
                subtitle = {
                    if (uiState.meetings.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.ask_history_meetings,
                                uiState.meetings.size,
                                uiState.meetings.size
                            ) + " · " + formatAskDuration(uiState.totalDurationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.semanticColors.onGradientVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(AppIcons.History, contentDescription = stringResource(R.string.ask_history_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.chatExists) {
                AskComposer(
                    value = input,
                    onValueChange = { input = it },
                    hint = stringResource(if (uiState.messages.isEmpty()) R.string.ask_input_hint else R.string.ask_input_followup_hint),
                    isAnswering = uiState.pending != null,
                    onSend = { send(input) },
                    onStop = viewModel::stop
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!uiState.isLoading && !uiState.chatExists) {
                Text(
                    text = stringResource(R.string.ask_chat_deleted),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
                return@Column
            }

            AskMeetingsStrip(
                meetings = uiState.meetings,
                titles = meetingTitles,
                missingCount = uiState.missingMeetingCount,
                onEdit = onEdit
            )

            if (showEmptyState) {
                AskEmptyState(
                    meetingCount = uiState.meetings.size,
                    onSuggestion = ::send,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        when (message.role) {
                            AskRole.USER -> AskUserBubble(message.text)
                            AskRole.ASSISTANT -> AskAssistantMessage(
                                message = message,
                                meetingTitles = meetingTitles,
                                canRegenerate = uiState.pending == null && message.id == uiState.messages.last().id,
                                onCitation = { viewModel.openSource(message.id, it) },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(plainAnswerText(message.text)))
                                    scope.launch { snackbarHostState.showSnackbar(copiedText) }
                                },
                                onRegenerate = { viewModel.regenerate(message.id) },
                                onAddMeetings = onEdit
                            )
                        }
                    }
                    uiState.pending?.let { pending ->
                        item(key = "pending") { AskPendingAnswer(pending) }
                    }
                    if (uiState.unanswered) {
                        item(key = "unanswered") {
                            AskUnanswered(
                                error = uiState.error,
                                onRetry = viewModel::retry,
                                onDownloadModel = onDownloadModel,
                                onAddMeetings = onEdit
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.source?.let { source ->
        AskSourceSheet(
            source = source,
            meetingTitle = meetingTitles[source.citation.sessionId],
            onDismiss = viewModel::closeSource,
            onShowIndex = viewModel::showSourceAt,
            onOpenSession = {
                viewModel.closeSource()
                onOpenSession(source.citation.sessionId, source.citation.segmentId)
            }
        )
    }
}

@Composable
private fun AskMeetingsStrip(
    meetings: List<TranscriptionSession>,
    titles: Map<String, String>,
    missingCount: Int,
    onEdit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(R.plurals.ask_reading_meetings, meetings.size, meetings.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) {
                Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ask_edit_meetings))
            }
        }
        if (meetings.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(meetings, key = { it.id }) { meeting ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = titles[meeting.id].orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        if (missingCount > 0) {
            AskInlineNote(
                text = pluralStringResource(R.plurals.ask_meetings_deleted, missingCount, missingCount),
                color = MaterialTheme.semanticColors.warning,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(AppIcons.Info, contentDescription = null, tint = MaterialTheme.semanticColors.warning, modifier = Modifier.size(14.dp))
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun AskEmptyState(meetingCount: Int, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        stringResource(R.string.ask_suggestion_decisions),
        stringResource(R.string.ask_suggestion_actions),
        stringResource(R.string.ask_suggestion_open),
        stringResource(R.string.ask_suggestion_summary)
    )
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AskAssistantBadge(size = 48.dp)
        Text(stringResource(R.string.ask_empty_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = pluralStringResource(R.plurals.ask_empty_body, meetingCount, meetingCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AskSectionHeader(stringResource(R.string.ask_try_asking), Modifier.padding(top = 12.dp))
        suggestions.forEach { suggestion ->
            Surface(
                onClick = { onSuggestion(suggestion) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(suggestion, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = AppIcons.SuggestionArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AskUserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AskAssistantHeader(status: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AskAssistantBadge()
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ask_assistant_name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (status != null) {
            Text(
                text = " · $status",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AskAssistantMessage(
    message: AskMessage,
    meetingTitles: Map<String, String>,
    canRegenerate: Boolean,
    onCitation: (Int) -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onAddMeetings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AskAssistantHeader(
            status = when {
                message.notFound -> stringResource(R.string.ask_status_not_found)
                message.citations.isNotEmpty() -> pluralStringResource(
                    R.plurals.ask_status_sources,
                    message.citations.size,
                    message.citations.size
                )
                else -> null
            }
        )

        val body = message.text.ifBlank { stringResource(R.string.ask_not_found_default) }
        Text(
            text = rememberAnswerText(body, onCitation),
            style = MaterialTheme.typography.bodyLarge
        )

        if (message.notFound) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ask_not_found_hint), style = MaterialTheme.typography.titleSmall)
                    Button(onClick = onAddMeetings) { Text(stringResource(R.string.ask_add_meetings)) }
                }
            }
        }

        if (message.citations.isNotEmpty()) {
            AskSourcesCard(message.citations, meetingTitles, onCitation)
        }

        Row(modifier = Modifier.padding(start = 0.dp)) {
            IconButton(onClick = onCopy) {
                Icon(AppIcons.ContentCopy, contentDescription = stringResource(R.string.ask_copy_answer), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canRegenerate) {
                IconButton(onClick = onRegenerate) {
                    Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.ask_regenerate), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun citationTitle(citation: AskCitation, meetingTitles: Map<String, String>): String =
    citation.sessionName?.takeIf { it.isNotBlank() }
        ?: meetingTitles[citation.sessionId]
        ?: stringResource(R.string.ask_meeting_generic)

@Composable
private fun citationMeta(citation: AskCitation): String = buildList {
    if (citation.segmentId == null) add(stringResource(R.string.ask_ai_summary))
    citation.offsetMs?.let { add(formatAskOffset(it)) }
    citation.speaker?.takeIf { it.isNotBlank() }?.let { add(it) }
}.joinToString(" · ")

@Composable
private fun AskSourcesCard(
    citations: List<AskCitation>,
    meetingTitles: Map<String, String>,
    onCitation: (Int) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            AskSectionHeader(
                text = stringResource(R.string.ask_sources),
                modifier = Modifier.padding(start = 14.dp, end = 14.dp)
            )
            citations.forEach { citation ->
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onCitation(citation.number) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AskNumberBadge(citation.number)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val meta = citationMeta(citation)
                        Text(
                            text = citationTitle(citation, meetingTitles) + if (meta.isNotEmpty()) " · $meta" else "",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "“${citation.quote}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AskNumberBadge(number: Int) {
    val description = stringResource(R.string.ask_citation_description, number)
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun AskPendingAnswer(pending: AskPending) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AskAssistantHeader(
            status = when (pending.phase) {
                AskPending.Phase.PREPARING -> stringResource(R.string.ask_status_preparing)
                AskPending.Phase.READING -> pluralStringResource(R.plurals.ask_status_reading, pending.meetingCount, pending.meetingCount)
                AskPending.Phase.WRITING -> stringResource(R.string.ask_status_writing)
            }
        )
        if (pending.partial.isNotBlank()) {
            Text(pending.partial, style = MaterialTheme.typography.bodyLarge)
        } else {
            // Placeholder lines while the model loads and reads.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1f, 0.92f, 0.64f).forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun AskUnanswered(
    error: AskError?,
    onRetry: () -> Unit,
    onDownloadModel: () -> Unit,
    onAddMeetings: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    when (error) {
                        AskError.MODEL_NOT_DOWNLOADED -> R.string.ask_error_model
                        AskError.MODEL_BUSY -> R.string.ask_error_busy
                        AskError.NO_MEETINGS -> R.string.ask_error_no_meetings
                        AskError.FAILED -> R.string.ask_error_failed
                        null -> R.string.ask_unanswered
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            when (error) {
                AskError.MODEL_NOT_DOWNLOADED -> Button(onClick = onDownloadModel) {
                    Text(stringResource(R.string.ask_error_model_action))
                }
                AskError.NO_MEETINGS -> Button(onClick = onAddMeetings) {
                    Text(stringResource(R.string.ask_add_meetings))
                }
                else -> OutlinedButton(onClick = onRetry) {
                    Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ask_retry))
                }
            }
        }
    }
}

@Composable
private fun AskComposer(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    isAnswering: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val shape = RoundedCornerShape(28.dp)
            val label = stringResource(R.string.ask_input_label)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = label },
                    placeholder = { Text(hint) },
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                if (isAnswering) {
                    FilledIconButton(onClick = onStop, modifier = Modifier.size(48.dp)) {
                        Icon(AppIcons.Stop, contentDescription = stringResource(R.string.ask_stop))
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = value.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors()
                    ) {
                        Icon(AppIcons.Send, contentDescription = stringResource(R.string.ask_send))
                    }
                }
            }
            AskInlineNote(
                text = stringResource(R.string.ask_disclaimer),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskSourceSheet(
    source: AskSourceState,
    meetingTitle: String?,
    onDismiss: () -> Unit,
    onShowIndex: (Int) -> Unit,
    onOpenSession: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AskSectionHeader(
                    text = stringResource(R.string.ask_source_position, source.index + 1, source.citations.size),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onShowIndex(source.index - 1) }, enabled = source.index > 0) {
                    Icon(AppIcons.ChevronLeft, contentDescription = stringResource(R.string.ask_source_previous))
                }
                IconButton(onClick = { onShowIndex(source.index + 1) }, enabled = source.index < source.citations.lastIndex) {
                    Icon(AppIcons.ChevronRight, contentDescription = stringResource(R.string.ask_source_next))
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = source.sessionName?.takeIf { it.isNotBlank() }
                        ?: meetingTitle
                        ?: source.citation.sessionName
                        ?: stringResource(R.string.ask_meeting_generic),
                    style = MaterialTheme.typography.headlineSmall
                )
                source.sessionCreatedAt?.takeIf { source.sessionExists }?.let { createdAt ->
                    val modeLabel = source.sessionMode?.let { askModeLabel(it) }
                    Text(
                        text = listOfNotNull(
                            askRelativeDate(createdAt),
                            modeLabel,
                            formatAskDuration(source.sessionDurationMs)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    source.isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    source.lines.isEmpty() -> AskSourceLineView(
                        speaker = source.citation.speaker,
                        offsetMs = source.citation.offsetMs,
                        text = source.quote,
                        cited = true
                    )
                    else -> source.lines.forEach { line ->
                        AskSourceLineView(line.speaker, line.offsetMs, line.text, line.cited)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(
                        when {
                            !source.sessionExists -> R.string.ask_source_deleted
                            source.citation.segmentId == null -> R.string.ask_source_summary_note
                            else -> R.string.ask_source_quote_note
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (source.sessionExists) {
                    Button(
                        onClick = onOpenSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.ask_source_open_session))
                        Spacer(Modifier.width(6.dp))
                        Icon(AppIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AskSourceLineView(speaker: String?, offsetMs: Long?, text: String, cited: Boolean) {
    val meta = listOfNotNull(speaker?.takeIf { it.isNotBlank() }, offsetMs?.let { formatAskOffset(it) }).joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (cited) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cited) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (cited) {
                    Icon(
                        imageVector = AppIcons.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.ask_source_cited),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (cited) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
