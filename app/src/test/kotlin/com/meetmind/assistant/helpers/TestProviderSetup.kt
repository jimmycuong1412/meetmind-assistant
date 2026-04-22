// Test helper: canonical sequence to save a key and enable a cloud provider.
// Mirrors the production invariant enforced by CloudProviderConfigRepository.setEnabled:
// updateConnectionStatus(true) must precede setEnabled(true) or an IllegalStateException is thrown.
package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.storage.CloudProviderConfigRepository

/**
 * Saves [rawKey] via [apiKeyStore], marks the provider as connected, then enables it.
 *
 * This is the only valid setup path for tests that exercise cloud inference —
 * calling [CloudProviderConfigRepository.setEnabled] without a prior
 * [CloudProviderConfigRepository.updateConnectionStatus] throws [IllegalStateException].
 */
suspend fun CloudProviderConfigRepository.enableProvider(
    provider: CloudProvider,
    apiKeyStore: FakeApiKeyStore,
    rawKey: String = "sk-ant-test-key"
) {
    apiKeyStore.saveKey(provider, rawKey.toByteArray())
    updateConnectionStatus(provider, connected = true)
    setEnabled(provider, enabled = true)
}
