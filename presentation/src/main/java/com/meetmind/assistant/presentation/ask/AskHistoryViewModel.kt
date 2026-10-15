package com.meetmind.assistant.presentation.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.domain.model.AskChatSummary
import com.meetmind.assistant.domain.repository.AskChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A rename that can still be undone from the snackbar. */
data class AskRenameUndo(val chatId: String, val previousTitle: String?)

data class AskHistoryUiState(
    val isLoading: Boolean = true,
    val chats: List<AskChatSummary> = emptyList(),
    val lastRename: AskRenameUndo? = null
) {
    val pinned: List<AskChatSummary> get() = chats.filter { it.pinned }
    val others: List<AskChatSummary> get() = chats.filterNot { it.pinned }
}

/** Backs Saved chats: list, rename (with undo), pin and delete. */
@HiltViewModel
class AskHistoryViewModel @Inject constructor(
    private val askChatRepository: AskChatRepository
) : ViewModel() {

    private val lastRename = MutableStateFlow<AskRenameUndo?>(null)

    val uiState: StateFlow<AskHistoryUiState> = combine(
        askChatRepository.observeChatSummaries(),
        lastRename
    ) { chats, rename ->
        AskHistoryUiState(isLoading = false, chats = chats, lastRename = rename)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskHistoryUiState())

    fun rename(chatId: String, newTitle: String) {
        val chat = uiState.value.chats.firstOrNull { it.id == chatId } ?: return
        val trimmed = newTitle.trim().take(MAX_TITLE_LENGTH)
        if (trimmed.isEmpty() || trimmed == chat.displayTitle) return
        viewModelScope.launch {
            askChatRepository.renameChat(chatId, trimmed)
            lastRename.value = AskRenameUndo(chatId, chat.title)
        }
    }

    fun undoRename() {
        val undo = lastRename.value ?: return
        lastRename.value = null
        viewModelScope.launch { askChatRepository.renameChat(undo.chatId, undo.previousTitle) }
    }

    /** The snackbar for the last rename was dismissed or timed out. */
    fun clearRenameUndo() {
        lastRename.value = null
    }

    fun togglePinned(chatId: String) {
        val chat = uiState.value.chats.firstOrNull { it.id == chatId } ?: return
        viewModelScope.launch { askChatRepository.setPinned(chatId, !chat.pinned) }
    }

    fun delete(chatId: String) {
        viewModelScope.launch { askChatRepository.deleteChat(chatId) }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 60
    }
}
