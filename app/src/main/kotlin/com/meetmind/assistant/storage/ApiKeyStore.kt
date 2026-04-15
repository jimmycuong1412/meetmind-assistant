// T013: Encrypted API key storage — Tink AES-256-GCM + Android Keystore + DataStore Preferences
package com.meetmind.assistant.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.meetmind.assistant.data.model.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.apiKeyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "api_keys"
)

/**
 * Interface for storing and retrieving API keys in encrypted form.
 *
 * Raw key bytes are kept in memory only as long as needed and are
 * zeroed immediately after use. The plaintext key is NEVER written
 * to disk or logged.
 */
interface ApiKeyStore {
    /** Returns true if an encrypted key exists for [provider]. */
    suspend fun hasKey(provider: CloudProvider): Boolean

    /**
     * Returns the decrypted API key for [provider], or null if none is saved.
     * Caller is responsible for zeroing the returned [ByteArray] after use.
     */
    suspend fun getKey(provider: CloudProvider): ByteArray?

    /**
     * Encrypts [plaintextKey] and persists the ciphertext for [provider].
     * The [plaintextKey] array is zeroed before this function returns.
     */
    suspend fun saveKey(provider: CloudProvider, plaintextKey: ByteArray)

    /** Removes the stored key for [provider]. */
    suspend fun deleteKey(provider: CloudProvider)

    /** Emits true whenever a key is present for [provider]. */
    fun observeHasKey(provider: CloudProvider): Flow<Boolean>
}

/**
 * Tink-backed implementation.
 *
 * Key hierarchy:
 *  - Android Keystore holds the Tink master key (hardware-backed on API 28+)
 *  - Tink AES-256-GCM wraps per-provider ciphertext
 *  - Ciphertext stored in DataStore Preferences
 *
 * SharedPreferences name for the Tink keyset is `api_key_keyset_pref`
 * (excluded from cloud backup via backup_rules.xml — T004).
 */
class TinkApiKeyStore(private val context: Context) : ApiKeyStore {

    companion object {
        private const val KEYSTORE_MASTER_KEY_URI =
            "android-keystore://meetmind_api_key_master"
        private const val KEYSET_PREF_NAME = "api_key_keyset_pref"
        private const val KEYSET_NAME = "api_key_keyset"

        private fun prefKey(provider: CloudProvider) =
            byteArrayPreferencesKey("encrypted_key_${provider.name.lowercase()}")
    }

    private val aead: Aead by lazy {
        try {
            AeadConfig.register()
            val keysetHandle: KeysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(KEYSTORE_MASTER_KEY_URI)
                .build()
                .keysetHandle
            keysetHandle.getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialise Tink Aead: ${e.message}", e)
        }
    }

    override suspend fun hasKey(provider: CloudProvider): Boolean =
        getEncryptedBytes(provider) != null

    override suspend fun getKey(provider: CloudProvider): ByteArray? =
        withContext(Dispatchers.IO) {
            val ciphertext = getEncryptedBytes(provider) ?: return@withContext null
            try {
                aead.decrypt(ciphertext, provider.name.toByteArray())
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun saveKey(provider: CloudProvider, plaintextKey: ByteArray) {
        require(plaintextKey.isNotEmpty()) { "API key must not be blank" }
        withContext(Dispatchers.IO) {
            try {
                val ciphertext = aead.encrypt(plaintextKey, provider.name.toByteArray())
                context.apiKeyDataStore.edit { prefs ->
                    prefs[prefKey(provider)] = ciphertext
                }
            } finally {
                plaintextKey.fill(0) // zero plaintext regardless of success/failure
            }
        }
    }

    override suspend fun deleteKey(provider: CloudProvider) {
        withContext(Dispatchers.IO) {
            context.apiKeyDataStore.edit { prefs ->
                prefs.remove(prefKey(provider))
            }
        }
    }

    override fun observeHasKey(provider: CloudProvider): Flow<Boolean> =
        context.apiKeyDataStore.data.map { prefs ->
            prefs[prefKey(provider)] != null
        }

    // --- helpers ---

    private suspend fun getEncryptedBytes(provider: CloudProvider): ByteArray? =
        context.apiKeyDataStore.data
            .map { prefs -> prefs[prefKey(provider)] }
            .first()
}
