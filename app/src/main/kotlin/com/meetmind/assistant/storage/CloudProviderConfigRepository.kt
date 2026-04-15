// T014: Reactive repository for CloudProviderConfig — persists provider selection and enabled state
package com.meetmind.assistant.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.CloudProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.cloudConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cloud_provider_config"
)

/**
 * Persists and exposes [CloudProviderConfig] for each [CloudProvider].
 *
 * Design notes:
 * - `encryptedApiKey` is NOT stored here — it lives in [ApiKeyStore] (DataStore "api_keys").
 *   This repository only holds the metadata: which provider is selected, enabled state,
 *   connection status, and last-validated timestamp.
 * - [setEnabled] throws [IllegalStateException] if [connectionStatus] is not `true`.
 *   Callers must validate the key (via CloudKeyValidationService) before enabling.
 */
class CloudProviderConfigRepository(
    private val context: Context,
    private val apiKeyStore: ApiKeyStore
) {

    companion object {
        private fun enabledKey(p: CloudProvider) =
            booleanPreferencesKey("${p.name.lowercase()}_enabled")

        private fun statusKey(p: CloudProvider) =
            stringPreferencesKey("${p.name.lowercase()}_status") // "true" | "false" | "null"

        private fun validatedAtKey(p: CloudProvider) =
            longPreferencesKey("${p.name.lowercase()}_validated_at")
    }

    /** Emits the current [CloudProviderConfig] for [provider], updating on any change. */
    fun observe(provider: CloudProvider): Flow<CloudProviderConfig> =
        combine(
            context.cloudConfigDataStore.data,
            apiKeyStore.observeHasKey(provider)
        ) { prefs, hasKey ->
            val statusStr = prefs[statusKey(provider)]
            CloudProviderConfig(
                provider = provider,
                // encryptedApiKey is not held here; presence is signalled via hasKey
                encryptedApiKey = if (hasKey) ByteArray(0) else null,
                isEnabled = prefs[enabledKey(provider)] ?: false,
                connectionStatus = when (statusStr) {
                    "true" -> true
                    "false" -> false
                    else -> null
                },
                lastValidatedAt = prefs[validatedAtKey(provider)]
            )
        }

    /**
     * Switch the active [CloudProvider].
     * Disables whatever was previously enabled; does not automatically enable [provider].
     */
    suspend fun setProvider(provider: CloudProvider) {
        withContext(Dispatchers.IO) {
            context.cloudConfigDataStore.edit { prefs ->
                // disable all providers first
                CloudProvider.entries.forEach { p ->
                    prefs[enabledKey(p)] = false
                }
                // caller still needs to call setEnabled(provider, true) after validation
            }
        }
    }

    /**
     * Enable or disable cloud inference for [provider].
     *
     * @throws IllegalStateException if [enabled] is `true` but the key has not been
     *         successfully validated (connectionStatus != true).
     */
    suspend fun setEnabled(provider: CloudProvider, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            if (enabled) {
                // Guard: must have a validated key before enabling
                var isConnected = false
                context.cloudConfigDataStore.data.map { prefs ->
                    prefs[statusKey(provider)] == "true"
                }.collect { isConnected = it }

                check(isConnected) {
                    "Cannot enable cloud inference for $provider: key not validated. " +
                        "Call CloudKeyValidationService first."
                }
            }
            context.cloudConfigDataStore.edit { prefs ->
                prefs[enabledKey(provider)] = enabled
            }
        }
    }

    /**
     * Record the result of a key validation attempt.
     *
     * @param provider   The provider whose key was validated
     * @param connected  True if validation succeeded, false if key was rejected
     */
    suspend fun updateConnectionStatus(provider: CloudProvider, connected: Boolean) {
        withContext(Dispatchers.IO) {
            context.cloudConfigDataStore.edit { prefs ->
                prefs[statusKey(provider)] = connected.toString()
                if (connected) {
                    prefs[validatedAtKey(provider)] = System.currentTimeMillis()
                }
                // If validation failed, disable cloud for this provider
                if (!connected) {
                    prefs[enabledKey(provider)] = false
                }
            }
        }
    }

    /**
     * Remove all persisted config for [provider] (does NOT delete the API key —
     * call [ApiKeyStore.deleteKey] separately).
     */
    suspend fun clearConfig(provider: CloudProvider) {
        withContext(Dispatchers.IO) {
            context.cloudConfigDataStore.edit { prefs ->
                prefs.remove(enabledKey(provider))
                prefs.remove(statusKey(provider))
                prefs.remove(validatedAtKey(provider))
            }
        }
    }
}
