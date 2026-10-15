package com.meetmind.assistant.presentation.ask

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.domain.model.AskCitation
import com.meetmind.assistant.domain.model.AskMessage
import com.meetmind.assistant.domain.model.AskRole
import com.meetmind.assistant.domain.model.RecordingMode
import com.meetmind.assistant.domain.model.TranscriptionSession
import com.meetmind.assistant.domain.monitor.CrashReporter
import com.meetmind.assistant.domain.repository.AskChatRepository
import com.meetmind.assistant.domain.repository.LlmRepository
import com.meetmind.assistant.domain.repository.SettingsRepository
import com.meetmind.assistant.domain.service.LlmProcessingServiceController
import com.meetmind.assistant.domain.usecase.ask.AskMeetingsUseCase
import com.meetmind.assistant.domain.usecase.llm.InitializeLlmUseCase
import com.meetmind.assistant.domain.usecase.transcription.GetAllSessionsUseCase
import com.meetmind.assistant.domain.usecase.transcription.GetSessionDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject

/** An answer being produced for [question]. */
data class AskPending(
    val question: String,
    val phase: Phase,
    val partial: String = "",
    val meetingCount: Int = 0
) {
    enum class Phase { PREPARING, READING, WRITING }
}

enum class AskError { MODEL_NOT_DOWNLOADED, MODEL_BUSY, NO_MEETINGS, FAILED }

/** One transcript line in the source sheet. */
data class AskSourceLine(
    val segmentId: String,
    val speaker: String?,
    val offsetMs: Long,
    val text: String,
    val cited: Boolean
)

/**
 * The source sheet opened from a citation.
 *
 * @property lines Transcript around the cited passage; empty when the meeting was
 *   deleted or the citation is an AI summary, in which case [quote] is shown.
 */
data class AskSourceState(
    val messageId: String,
    val citations: List<AskCitation>,
    val index: Int,
    val isLoading: Boolean = true,
    val sessionExists: Boolean = true,
    val sessionName: String? = null,
    val sessionCreatedAt: Long? = null,
    val sessionMode: RecordingMode? = null,
    val sessionDurationMs: Long = 0L,
    val lines: List<AskSourceLine> = emptyList()
) {
    val citation: AskCitation get() = citations[index]
    val quote: String get() = citation.quote
}

/**
 * @property meetings Meetings of the chat that still exist, in picked order.
 * @property missingMeetingCount Meetings picked for the chat that were deleted since.
 * @property pending Non-null while an answer is being produced.
 * @property unanswered The last question has no answer (stopped or failed) and can be retried.
 */
data class AskChatUiState(
    val isLoading: Boolean = true,
    val chatExists: Boolean = true,
    val meetings: List<TranscriptionSession> = emptyList(),
    val missingMeetingCount: Int = 0,
    val messages: List<AskMessage> = emptyList(),
    val pending: AskPending? = null,
    val error: AskError? = null,
    val source: AskSourceState? = null
) {
    val totalDurationMs: Long get() = meetings.sumOf { it.durationMs }

    val unanswered: Boolean
        get() = pending == null && messages.lastOrNull()?.role == AskRole.USER
}

/**
 * Backs one Ask MeetMind chat (navigation arg `chatId`).
 *
 * Answers run one at a time on the shared on-device model; the model is released
 * when the chat closes, mirroring the history-insight flow in Session Details.
 */
@HiltViewModel
class AskChatViewModel @Inject constructor(
    private val askChatRepository: AskChatRepository,
    private val askMeetingsUseCase: AskMeetingsUseCase,
    private val getSessionDetailsUseCase: GetSessionDetailsUseCase,
    private val initializeLlmUseCase: InitializeLlmUseCase,
    private val llmRepository: LlmRepository,
    private val settingsRepository: SettingsRepository,
    private val llmProcessingServiceController: LlmProcessingServiceController,
    private val crashReporter: CrashReporter,
    getAllSessionsUseCase: GetAllSessionsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle.get<String>("chatId")) { "chatId nav argument missing" }

    private val _uiState = MutableStateFlow(AskChatUiState())
    val uiState: StateFlow<AskChatUiState> = _uiState.asStateFlow()

    private var answerJob: Job? = null
    private var usedModel = false

    init {
        viewModelScope.launch {
            combine(askChatRepository.observeChat(chatId), getAllSessionsUseCase()) { chat, sessions ->
                chat to sessions
            }.collect { (chat, sessions) ->
                val byId = sessions.associateBy { it.id }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        chatExists = chat != null,
                        meetings = chat?.sessionIds?.mapNotNull { byId[it] }.orEmpty(),
                        missingMeetingCount = chat?.sessionIds?.count { it !in byId } ?: 0,
                        messages = chat?.messages.orEmpty()
                    )
                }
            }
        }
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _uiState.value.pending != null) return
        val history = _uiState.value.messages
        val message = AskMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = AskRole.USER,
            text = text,
            createdAt = System.currentTimeMillis()
        )
        answer(text, history) { askChatRepository.addMessage(message) }
    }

    /** Ask the last unanswered question again (after Stop or an error). */
    fun retry() {
        val messages = _uiState.value.messages
        val last = messages.lastOrNull()?.takeIf { it.role == AskRole.USER } ?: return
        answer(last.text, messages.dropLast(1))
    }

    /** Replace [assistantMessageId] with a fresh answer to the same question. */
    fun regenerate(assistantMessageId: String) {
        if (_uiState.value.pending != null) return
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == assistantMessageId }
        if (index <= 0 || messages[index - 1].role != AskRole.USER) return
        val question = messages[index - 1]
        answer(question.text, messages.subList(0, index - 1)) { askChatRepository.deleteMessage(assistantMessageId) }
    }

    fun stop() {
        answerJob?.cancel()
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun openSource(messageId: String, citationNumber: Int) {
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val index = message.citations.indexOfFirst { it.number == citationNumber }
        if (index < 0) return
        showSource(AskSourceState(messageId = messageId, citations = message.citations, index = index))
    }

    fun showSourceAt(index: Int) {
        val source = _uiState.value.source ?: return
        if (index !in source.citations.indices) return
        showSource(source.copy(index = index, isLoading = true, lines = emptyList()))
    }

    fun closeSource() {
        _uiState.update { it.copy(source = null) }
    }

    private fun showSource(initial: AskSourceState) {
        _uiState.update { it.copy(source = initial) }
        viewModelScope.launch {
            val citation = initial.citation
            val details = getSessionDetailsUseCase(citation.sessionId).first()
            val loaded = if (details == null) {
                initial.copy(isLoading = false, sessionExists = false, sessionName = citation.sessionName)
            } else {
                val session = details.session
                val segments = details.segments.filter { it.isComplete && it.text.isNotBlank() }.sortedBy { it.timestamp }
                val start = segments.indexOfFirst { it.id == citation.segmentId }
                val lines = if (start < 0) emptyList() else {
                    // The cited passage is a run of segments starting at segmentId;
                    // mark the ones whose text is part of the snapshotted quote.
                    var end = start
                    while (end + 1 < segments.size && citation.quote.contains(segments[end + 1].text.trim())) end++
                    val from = (start - CONTEXT_LINES).coerceAtLeast(0)
                    val to = (end + 1 + CONTEXT_LINES).coerceAtMost(segments.size)
                    (from until to).map { position ->
                        val segment = segments[position]
                        AskSourceLine(
                            segmentId = segment.id,
                            speaker = segment.speaker,
                            offsetMs = segment.startOffsetMs ?: (segment.timestamp - session.createdAt).coerceAtLeast(0),
                            text = segment.text.trim(),
                            cited = position in start..end
                        )
                    }
                }
                initial.copy(
                    isLoading = false,
                    sessionExists = true,
                    sessionName = session.name,
                    sessionCreatedAt = session.createdAt,
                    sessionMode = session.mode,
                    sessionDurationMs = session.durationMs,
                    lines = lines
                )
            }
            _uiState.update { state ->
                // Ignore a late load if the sheet was closed or moved to another source.
                val current = state.source
                if (current != null && current.messageId == initial.messageId && current.index == initial.index) {
                    state.copy(source = loaded)
                } else {
                    state
                }
            }
        }
    }

    private fun answer(question: String, history: List<AskMessage>, beforeAnswer: suspend () -> Unit = {}) {
        if (_uiState.value.pending != null) return
        _uiState.update {
            it.copy(pending = AskPending(question, AskPending.Phase.PREPARING, meetingCount = it.meetings.size), error = null)
        }
        answerJob = viewModelScope.launch {
            llmProcessingServiceController.startProcessing()
            try {
                beforeAnswer()

                val settings = settingsRepository.getSettings().first()
                if (settings.llmModelPath.isBlank()) {
                    fail(AskError.MODEL_NOT_DOWNLOADED)
                    return@launch
                }
                if (llmRepository.isGenerating) {
                    fail(AskError.MODEL_BUSY)
                    return@launch
                }
                if (settings.memoryConstrainedDetected) llmRepository.useConservativeThreads()
                initializeLlmUseCase(settings.llmModelPath, loadImmediately = false).getOrThrow()
                usedModel = true

                val sessions = _uiState.value.meetings.mapNotNull { getSessionDetailsUseCase(it.id).first() }
                if (sessions.isEmpty()) {
                    fail(AskError.NO_MEETINGS)
                    return@launch
                }

                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                askMeetingsUseCase(
                    sessions = sessions,
                    question = question,
                    history = history,
                    meetingLabel = { index -> dateFormat.format(Date(sessions[index].session.createdAt)) }
                ).collect { progress ->
                    when (progress) {
                        is AskMeetingsUseCase.Progress.Reading -> _uiState.update { state ->
                            state.copy(pending = state.pending?.copy(phase = AskPending.Phase.READING, meetingCount = progress.meetingCount))
                        }
                        is AskMeetingsUseCase.Progress.Writing -> _uiState.update { state ->
                            state.copy(pending = state.pending?.copy(phase = AskPending.Phase.WRITING, partial = progress.partial))
                        }
                        is AskMeetingsUseCase.Progress.Done -> askChatRepository.addMessage(
                            AskMessage(
                                id = UUID.randomUUID().toString(),
                                chatId = chatId,
                                role = AskRole.ASSISTANT,
                                text = progress.answer.text,
                                createdAt = System.currentTimeMillis(),
                                citations = progress.answer.citations,
                                notFound = progress.answer.notFound
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordNonFatal(e, "ask_meetings: answer failed")
                fail(AskError.FAILED)
            } finally {
                _uiState.update { it.copy(pending = null) }
                llmProcessingServiceController.stopProcessing()
            }
        }
    }

    private fun fail(error: AskError) {
        _uiState.update { it.copy(error = error) }
    }

    override fun onCleared() {
        answerJob?.cancel()
        val releaseModel = usedModel
        // viewModelScope is already cancelled here; finish the cleanup regardless.
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            askChatRepository.deleteChatIfEmpty(chatId)
            if (releaseModel && !llmRepository.isGenerating) {
                runCatching { llmRepository.cleanup() }
            }
        }
        super.onCleared()
    }

    private companion object {
        const val CONTEXT_LINES = 2
    }
}
