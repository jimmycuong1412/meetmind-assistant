// T016: In-memory ApiKeyStore for unit tests — avoids Android Keystore / Tink native deps
package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.storage.ApiKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ApiKeyStore] for unit tests.
 *
 * Does NOT use Tink or Android Keystore — suitable for JVM-only Robolectric tests.
 * Mimics the zeroing contract of [TinkApiKeyStore]: [plaintextKey] is filled with
 * zeros after [saveKey] returns.
 *
 * Throws [IllegalArgumentException] on blank key, matching [TinkApiKeyStore] behaviour.
 */
class FakeApiKeyStore : ApiKeyStore {

    private val keys = mutableMapOf<CloudProvider, ByteArray>()
    private val hasKeyFlows = mutableMapOf<CloudProvider, MutableStateFlow<Boolean>>()

    private fun hasKeyFlow(provider: CloudProvider): MutableStateFlow<Boolean> =
        hasKeyFlows.getOrPut(provider) { MutableStateFlow(keys.containsKey(provider)) }

    override suspend fun hasKey(provider: CloudProvider): Boolean = keys.containsKey(provider)

    override suspend fun getKey(provider: CloudProvider): ByteArray? =
        keys[provider]?.copyOf()

    override suspend fun saveKey(provider: CloudProvider, plaintextKey: ByteArray) {
        require(plaintextKey.isNotEmpty()) { "API key must not be blank" }
        try {
            keys[provider] = plaintextKey.copyOf()
            hasKeyFlow(provider).value = true
        } finally {
            plaintextKey.fill(0) // zero plaintext — mirrors TinkApiKeyStore contract
        }
    }

    override suspend fun deleteKey(provider: CloudProvider) {
        keys.remove(provider)
        hasKeyFlow(provider).value = false
    }

    override fun observeHasKey(provider: CloudProvider): Flow<Boolean> =
        hasKeyFlow(provider)
}
