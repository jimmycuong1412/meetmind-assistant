// T027: SessionViewModel — exposes badge state and streaming suggestion tokens
// T014 (spec 008): Added analysisEvent StateFlow + cadence lifecycle
// spec 009 — T019: migrated to @HiltViewModel @Inject constructor; Factory deleted
package com.meetmind.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.analysis.AnalysisEvent
import com.meetmind.assistant.analysis.AnalysisCadenceController
import com.meetmind.assistant.data.model.CloudBadgeState
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.CloudProviderConfig
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.SessionMode
import com.meetmind.assistant.inference.CloudBadgeController
import com.meetmind.assistant.inference.CloudInferenceEngine
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val cloudInferenceEngine: CloudInferenceEngine,
    private val configRepository: CloudProviderConfigRepository,
    /** Singleton cadence controller injected by Hilt; wired to the app-scoped shared buffer. */
    private val cadenceController: AnalysisCadenceController
) : ViewModel() {


    private val badgeController = CloudBadgeController(
        configRepository = configRepository,
        activeProvider = CloudProvider.GEMINI,
        scope = viewModelScope
    )

    /** T027: Drives the CloudBadge component in SessionScreen */
    val cloudBadgeState: StateFlow<CloudBadgeState> = badgeController.badgeState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudBadgeState.HIDDEN)

    /**
     * T032: Accumulates streaming tokens for the current suggestion.
     * Reset to empty on each new question detection.
     */
    private val _currentSuggestionTokens = MutableStateFlow("")
    val currentSuggestionTokens: StateFlow<String> = _currentSuggestionTokens.asStateFlow()

    /**
     * T014 (spec 008): Latest analysis event from the continuous analysis cadence.
     * Null when no analysis has run yet or analysisEnabled = false.
     * Reset to null when a question-detection event takes priority (FR-010 / T017).
     */
    private val _analysisEvent = MutableStateFlow<AnalysisEvent?>(null)
    val analysisEvent: StateFlow<AnalysisEvent?> = _analysisEvent.asStateFlow()

    /**
     * The active cloud config — whichever provider is currently enabled.
     */
    val activeCloudConfig: StateFlow<CloudProviderConfig> = combine(
        configRepository.observe(CloudProvider.CLAUDE),
        configRepository.observe(CloudProvider.GEMINI)
    ) { claude, gemini ->
        when {
            claude.isEnabled -> claude
            gemini.isEnabled -> gemini
            else -> gemini
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CloudProviderConfig(provider = CloudProvider.GEMINI, encryptedApiKey = null)
    )

    init {
        // T014: Start cadence controller and collect its events into analysisEvent StateFlow
        cadenceController.start(viewModelScope)
        cadenceController.events
            .onEach { event ->
                // FR-010 / T017: suppress analysis card if a question suggestion is active
                if (_currentSuggestionTokens.value.isBlank()) {
                    _analysisEvent.value = event
                }
            }
            .launchIn(viewModelScope)
    }

    /** Called by the audio processing service when a new question is detected. */
    fun onInferenceEvent(event: InferenceEvent) {
        badgeController.onInferenceEvent(event)
        when (event) {
            is InferenceEvent.Token -> {
                _currentSuggestionTokens.value += event.text
            }
            is InferenceEvent.Complete -> {
                // Final text already accumulated via Token events; nothing extra needed
            }
            is InferenceEvent.FallbackActivated,
            is InferenceEvent.Error -> Unit
        }
    }

    /** Reset token stream when a new question is detected */
    fun onNewQuestion() {
        _currentSuggestionTokens.value = ""
        // T017: Clear any pending analysis card when a question takes priority
        _analysisEvent.value = null
        badgeController.onNewQuestion()
    }

    /**
     * Submit a question for cloud inference. Resets the current suggestion,
     * then streams inference events into [onInferenceEvent].
     */
    fun onQuestionDetected(
        questionText: String,
        sessionMode: SessionMode = SessionMode.INTERVIEW
    ) {
        if (questionText.isBlank()) return

        onNewQuestion()

        val provider = activeCloudConfig.value.provider
        val request = CloudInferenceRequest(
            questionText = questionText,
            systemPrompt = "You are a helpful assistant in a professional meeting or interview. " +
                "Answer concisely and directly.",
            provider = provider,
            sessionMode = sessionMode
        )

        viewModelScope.launch {
            cloudInferenceEngine.streamSuggestion(request)
                .onEach { event -> onInferenceEvent(event) }
                .launchIn(this)
        }
    }

    /** T034 (spec 008): Dismiss analysis card — called from SessionScreen dismiss button. */
    fun dismissAnalysisEvent() {
        _analysisEvent.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // T015 (spec 008): Cancel cadence on session end to prevent in-flight inference (FR-015)
        cadenceController.stop()
    }
}
