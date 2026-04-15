// HomeViewModel — exposes cloud config state and toggle for HomeScreen
package com.meetmind.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.CloudProviderConfig
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val configRepository: CloudProviderConfigRepository
) : ViewModel() {

    /**
     * Config of the "active" provider — whichever provider has a saved + validated key.
     * Prefers Claude if enabled; falls back to Gemini; falls back to whichever has a
     * valid connection; otherwise defaults to GEMINI config.
     */
    val activeCloudConfig: StateFlow<CloudProviderConfig> = combine(
        configRepository.observe(CloudProvider.CLAUDE),
        configRepository.observe(CloudProvider.GEMINI)
    ) { claude, gemini ->
        when {
            claude.isEnabled -> claude
            gemini.isEnabled -> gemini
            claude.connectionStatus == true -> claude
            gemini.connectionStatus == true -> gemini
            else -> gemini
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CloudProviderConfig(provider = CloudProvider.GEMINI, encryptedApiKey = null)
    )

    val isCloudEnabled: StateFlow<Boolean> = activeCloudConfig
        .map { it.isEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onToggleCloud(enabled: Boolean) {
        viewModelScope.launch {
            try {
                configRepository.setEnabled(activeCloudConfig.value.provider, enabled)
            } catch (_: IllegalStateException) {
                // Key not validated — toggle silently ignored (UI guard prevents this)
            }
        }
    }

    class Factory(
        private val configRepository: CloudProviderConfigRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(configRepository) as T
    }
}
