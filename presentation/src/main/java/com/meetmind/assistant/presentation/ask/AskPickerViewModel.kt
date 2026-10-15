package com.meetmind.assistant.presentation.ask

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.domain.model.TranscriptionSession
import com.meetmind.assistant.domain.repository.AskChatRepository
import com.meetmind.assistant.domain.usecase.transcription.GetAllSessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for picking the meetings an Ask MeetMind chat reads.
 *
 * @property sessions Sessions matching [query], newest first.
 * @property selectedIds Picked sessions, in the order they were picked.
 * @property isEditing True when changing the meetings of an existing chat.
 */
data class AskPickerUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val sessions: List<TranscriptionSession> = emptyList(),
    val selectedIds: List<String> = emptyList(),
    val selectedDurationMs: Long = 0L,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false
) {
    val canAsk: Boolean get() = selectedIds.isNotEmpty() && !isSaving

    /**
     * Past this much recording the excerpts no longer fit one prompt, so answers
     * draw on the most relevant parts. Shown as a hint, never a block.
     */
    val isLongSelection: Boolean get() = selectedDurationMs > LONG_SELECTION_MS

    companion object {
        const val LONG_SELECTION_MS = 2 * 60 * 60 * 1000L
    }
}

/**
 * Backs the meeting picker. Navigation args: optional `chatId` (edit that chat's
 * meetings) and optional `sessionId` (pre-select one meeting).
 */
@HiltViewModel
class AskPickerViewModel @Inject constructor(
    getAllSessionsUseCase: GetAllSessionsUseCase,
    private val askChatRepository: AskChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editingChatId: String? = savedStateHandle.get<String>("chatId")?.takeIf { it.isNotBlank() }
    private val preselectedSessionId: String? = savedStateHandle.get<String>("sessionId")?.takeIf { it.isNotBlank() }

    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow(listOfNotNull(preselectedSessionId))
    private val saving = MutableStateFlow(false)

    private val _openChat = Channel<String>(Channel.BUFFERED)

    /** Emits the id of the chat to open once the selection is confirmed. */
    val openChat: Flow<String> = _openChat.receiveAsFlow()

    val uiState: StateFlow<AskPickerUiState> = combine(
        getAllSessionsUseCase(),
        query,
        selection,
        saving
    ) { sessions, q, selected, isSaving ->
        val needle = q.trim()
        val visible = if (needle.isEmpty()) sessions else sessions.filter { session ->
            session.name?.contains(needle, ignoreCase = true) == true ||
                session.topic?.contains(needle, ignoreCase = true) == true
        }
        val byId = sessions.associateBy { it.id }
        // Drop ids of meetings deleted since the chat was created.
        val existingSelection = selected.filter { it in byId }
        AskPickerUiState(
            isLoading = false,
            query = q,
            sessions = visible,
            selectedIds = existingSelection,
            selectedDurationMs = existingSelection.sumOf { byId.getValue(it).durationMs },
            isEditing = editingChatId != null,
            isSaving = isSaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskPickerUiState(isEditing = editingChatId != null))

    init {
        if (editingChatId != null) {
            viewModelScope.launch {
                askChatRepository.observeChat(editingChatId).first()?.let { chat ->
                    selection.value = chat.sessionIds
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggle(sessionId: String) {
        selection.update { current ->
            if (sessionId in current) current - sessionId else current + sessionId
        }
    }

    fun clearSelection() {
        selection.value = emptyList()
    }

    fun confirm() {
        val state = uiState.value
        if (!state.canAsk) return
        saving.value = true
        viewModelScope.launch {
            try {
                val chatId = if (editingChatId != null) {
                    askChatRepository.updateChatSessions(editingChatId, state.selectedIds)
                    editingChatId
                } else {
                    askChatRepository.createChat(state.selectedIds)
                }
                _openChat.send(chatId)
            } finally {
                saving.value = false
            }
        }
    }
}
