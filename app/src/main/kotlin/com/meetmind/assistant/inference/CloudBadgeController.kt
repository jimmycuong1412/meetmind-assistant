// T024: Derives CloudBadgeState from config + inference events
package com.meetmind.assistant.inference

import com.meetmind.assistant.data.model.CloudBadgeState
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Combines [CloudProviderConfigRepository] and the inference event stream to derive
 * [CloudBadgeState] for display on the session screen.
 *
 * Badge lifecycle per session:
 *  - Session starts with cloud enabled → CLOUD_ACTIVE (optimistic)
 *  - [InferenceEvent.FallbackActivated] received → FALLBACK
 *  - Next [InferenceEvent.Token] or [InferenceEvent.Complete] → CLOUD_ACTIVE (auto-reset)
 *  - Cloud disabled or no key → HIDDEN
 */
class CloudBadgeController(
    private val configRepository: CloudProviderConfigRepository,
    private val activeProvider: CloudProvider,
    scope: CoroutineScope
) {
    private val _badgeState = MutableStateFlow(CloudBadgeState.HIDDEN)
    val badgeState: StateFlow<CloudBadgeState> = _badgeState.asStateFlow()

    init {
        // React to config changes (enabled / disabled)
        configRepository.observe(activeProvider)
            .onEach { config ->
                if (!config.isEnabled || config.encryptedApiKey == null) {
                    _badgeState.value = CloudBadgeState.HIDDEN
                } else if (_badgeState.value == CloudBadgeState.HIDDEN) {
                    // Optimistic: show CLOUD_ACTIVE when cloud is enabled and session starts
                    _badgeState.value = CloudBadgeState.CLOUD_ACTIVE
                }
            }
            .launchIn(scope)
    }

    /** Call when a new question is detected to reset badge to CLOUD_ACTIVE (optimistic). */
    fun onNewQuestion() {
        if (_badgeState.value != CloudBadgeState.HIDDEN) {
            _badgeState.value = CloudBadgeState.CLOUD_ACTIVE
        }
    }

    /** Route inference events to update badge state. */
    fun onInferenceEvent(event: InferenceEvent) {
        when (event) {
            is InferenceEvent.Token,
            is InferenceEvent.Complete -> {
                if (_badgeState.value != CloudBadgeState.HIDDEN) {
                    _badgeState.value = CloudBadgeState.CLOUD_ACTIVE
                }
            }
            is InferenceEvent.FallbackActivated -> {
                _badgeState.value = CloudBadgeState.FALLBACK
            }
            is InferenceEvent.Error -> {
                _badgeState.value = CloudBadgeState.FALLBACK
            }
        }
    }
}
