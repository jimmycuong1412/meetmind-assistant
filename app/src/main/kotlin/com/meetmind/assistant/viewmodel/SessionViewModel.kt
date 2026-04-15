// T027: SessionViewModel — exposes badge state and streaming suggestion tokens
package com.meetmind.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.data.model.CloudBadgeState
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.inference.CloudBadgeController
import com.meetmind.assistant.inference.CloudInferenceEngine
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class SessionViewModel(
    private val cloudInferenceEngine: CloudInferenceEngine,
    configRepository: CloudProviderConfigRepository,
    activeProvider: CloudProvider = CloudProvider.GEMINI
) : ViewModel() {

    private val badgeController = CloudBadgeController(
        configRepository = configRepository,
        activeProvider = activeProvider,
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
        badgeController.onNewQuestion()
    }

    class Factory(
        private val cloudInferenceEngine: CloudInferenceEngine,
        private val configRepository: CloudProviderConfigRepository,
        private val activeProvider: CloudProvider = CloudProvider.GEMINI
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionViewModel(cloudInferenceEngine, configRepository, activeProvider) as T
    }
}
