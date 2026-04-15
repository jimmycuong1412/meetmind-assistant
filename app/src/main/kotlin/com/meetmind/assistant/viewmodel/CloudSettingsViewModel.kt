// T021: ViewModel for Cloud AI Settings screen
package com.meetmind.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.CloudProviderConfig
import com.meetmind.assistant.inference.CloudKeyValidationService
import com.meetmind.assistant.inference.ValidationResult
import com.meetmind.assistant.storage.ApiKeyStore
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ValidationState {
    object Idle : ValidationState()
    object Validating : ValidationState()
    object Success : ValidationState()
    data class Failure(val reason: ValidationResult) : ValidationState()
}

class CloudSettingsViewModel(
    private val apiKeyStore: ApiKeyStore,
    private val configRepository: CloudProviderConfigRepository,
    private val validationService: CloudKeyValidationService
) : ViewModel() {

    /** Currently selected provider in the UI (does not change the saved config until Save) */
    private val _selectedProvider = MutableStateFlow(CloudProvider.GEMINI)
    val selectedProvider: StateFlow<CloudProvider> = _selectedProvider.asStateFlow()

    /** Live config for Gemini */
    val geminiConfig: StateFlow<CloudProviderConfig> = configRepository
        .observe(CloudProvider.GEMINI)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultConfig(CloudProvider.GEMINI))

    /** Live config for Claude */
    val claudeConfig: StateFlow<CloudProviderConfig> = configRepository
        .observe(CloudProvider.CLAUDE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultConfig(CloudProvider.CLAUDE))

    /** Tracks the result of the most recent Save + Validate action */
    private val _validationState = MutableStateFlow<ValidationState>(ValidationState.Idle)
    val validationState: StateFlow<ValidationState> = _validationState.asStateFlow()

    fun onSelectProvider(provider: CloudProvider) {
        _selectedProvider.value = provider
    }

    /**
     * Validate [keyText] for [provider] then persist if valid.
     * On success: saves encrypted key + marks connectionStatus = true.
     * On failure: does NOT persist the key; updates connectionStatus = false.
     */
    fun onSaveKey(provider: CloudProvider, keyText: String) {
        viewModelScope.launch {
            _validationState.value = ValidationState.Validating
            val result = validationService.validate(provider, keyText)
            if (result == ValidationResult.SUCCESS) {
                // Save encrypted key then mark connected
                apiKeyStore.saveKey(provider, keyText.toByteArray())
                configRepository.updateConnectionStatus(provider, connected = true)
                _validationState.value = ValidationState.Success
            } else {
                configRepository.updateConnectionStatus(provider, connected = false)
                _validationState.value = ValidationState.Failure(result)
            }
        }
    }

    /**
     * Remove the saved key and clear connection status for [provider].
     * Also disables cloud inference for that provider.
     */
    fun onDeleteKey(provider: CloudProvider) {
        viewModelScope.launch {
            apiKeyStore.deleteKey(provider)
            configRepository.clearConfig(provider)
            _validationState.value = ValidationState.Idle
        }
    }

    private fun defaultConfig(provider: CloudProvider) = CloudProviderConfig(
        provider = provider,
        encryptedApiKey = null
    )

    class Factory(
        private val apiKeyStore: ApiKeyStore,
        private val configRepository: CloudProviderConfigRepository,
        private val validationService: CloudKeyValidationService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CloudSettingsViewModel(apiKeyStore, configRepository, validationService) as T
    }
}
