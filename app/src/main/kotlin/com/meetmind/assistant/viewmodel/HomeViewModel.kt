// HomeViewModel — exposes cloud config state and toggle for HomeScreen
// spec 009 — T018: migrated to @HiltViewModel @Inject constructor; Factory deleted
package com.meetmind.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.CloudProviderConfig
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configRepository: CloudProviderConfigRepository
) : ViewModel() {

    /**
     * Config of the "active" provider — whichever provider is currently enabled.
     * Prefers Claude; falls back to Gemini; defaults to GEMINI config when neither is enabled.
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
}
