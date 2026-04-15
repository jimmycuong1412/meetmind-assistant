// T010: Per-provider configuration stored in DataStore (API key ciphertext stored via Tink)
package com.meetmind.assistant.data.model

/**
 * Runtime configuration for a single cloud AI provider.
 *
 * NOTE: [encryptedApiKey] is the Tink-AES256-GCM ciphertext of the raw key.
 *       It is NEVER stored in plaintext and NEVER logged.
 *       The plaintext key is decrypted in-process only when a request is made.
 */
data class CloudProviderConfig(
    /** Which provider this config belongs to */
    val provider: CloudProvider,

    /**
     * AES-256-GCM ciphertext of the API key, produced by [ApiKeyStore].
     * Null if no key has been saved yet.
     */
    val encryptedApiKey: ByteArray?,

    /** Whether cloud inference is enabled for this provider */
    val isEnabled: Boolean = false,

    /**
     * Last known validation result.
     * `true`  = last validation passed
     * `false` = last validation failed
     * `null`  = not yet validated
     */
    val connectionStatus: Boolean? = null,

    /** Epoch ms of the most recent successful validation, or null */
    val lastValidatedAt: Long? = null
) {
    // ByteArray needs custom equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CloudProviderConfig) return false
        return provider == other.provider &&
            encryptedApiKey.contentEquals(other.encryptedApiKey) &&
            isEnabled == other.isEnabled &&
            connectionStatus == other.connectionStatus &&
            lastValidatedAt == other.lastValidatedAt
    }

    override fun hashCode(): Int {
        var result = provider.hashCode()
        result = 31 * result + (encryptedApiKey?.contentHashCode() ?: 0)
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + (connectionStatus?.hashCode() ?: 0)
        result = 31 * result + (lastValidatedAt?.hashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEquals(other: ByteArray?): Boolean =
    if (this == null && other == null) true
    else if (this == null || other == null) false
    else this.contentEquals(other)
