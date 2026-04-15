// T017: Validates an API key for a given cloud provider via a minimal test call
package com.meetmind.assistant.inference

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.meetmind.assistant.data.model.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ValidationResult {
    SUCCESS,
    INVALID_KEY,
    NETWORK_ERROR,
    PROVIDER_ERROR
}

/**
 * Validates that cloud provider credentials are working.
 *
 * Gemini: Firebase AI Logic SDK uses google-services.json for project config.
 *   The API key stored in [ApiKeyStore] is not passed directly to the SDK;
 *   instead we verify connectivity by attempting a minimal generateContent call.
 *   (For Gemini, "key validation" means verifying the Firebase project is reachable
 *    and the AI Logic SDK initialises correctly.)
 *
 * Claude: API key is passed directly to AnthropicOkHttpClient and validated
 *   via a 1-token message request.
 */
open class CloudKeyValidationService {

    open suspend fun validate(
        provider: CloudProvider,
        apiKey: String
    ): ValidationResult = withContext(Dispatchers.IO) {
        // Gemini uses google-services.json — no per-call API key required
        if (provider == CloudProvider.CLAUDE && apiKey.isBlank()) {
            return@withContext ValidationResult.INVALID_KEY
        }

        val result = withTimeoutOrNull(10_000L) {
            when (provider) {
                CloudProvider.GEMINI -> validateGemini()
                CloudProvider.CLAUDE -> validateClaude(apiKey)
            }
        }
        result ?: ValidationResult.NETWORK_ERROR
    }

    private suspend fun validateGemini(): ValidationResult {
        return try {
            // Firebase AI Logic SDK uses google-services.json for auth —
            // we validate by confirming the model initialises and responds.
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel("gemini-2.5-flash")
            model.generateContent(content { text("hi") })
            ValidationResult.SUCCESS
        } catch (e: Exception) {
            classifyError(e)
        }
    }

    private suspend fun validateClaude(apiKey: String): ValidationResult {
        return try {
            val client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build()
            val params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(1)
                .addUserMessage("hi")
                .build()
            client.messages().create(params)
            ValidationResult.SUCCESS
        } catch (e: Exception) {
            classifyError(e)
        }
    }

    private fun classifyError(e: Exception): ValidationResult {
        val msg = e.message?.lowercase() ?: ""
        return when {
            "401" in msg || "403" in msg || "authentication" in msg ||
                "api key" in msg || "invalid" in msg -> ValidationResult.INVALID_KEY
            "network" in msg || "connect" in msg || "timeout" in msg ->
                ValidationResult.NETWORK_ERROR
            else -> ValidationResult.PROVIDER_ERROR
        }
    }
}
