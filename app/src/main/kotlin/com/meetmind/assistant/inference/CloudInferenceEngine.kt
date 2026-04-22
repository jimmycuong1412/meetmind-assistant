// T030: Routes inference requests to the active cloud provider with 5s fallback to on-device
// T010: Refactored to inject CloudStreamingProvider, Clock, and connectivityChecker for testability
// spec 009 — T015: claudeProviderFactory moved inside class body; constructor usable by Hilt @Inject
package com.meetmind.assistant.inference

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.FallbackReason
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.storage.ApiKeyStore
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * Routes detected questions to Gemini or Claude and falls back to on-device
 * inference when the cloud call fails or times out.
 *
 * Constitution v2.0 data minimisation enforced here:
 *  - questionText truncated to 150 tokens (~600 chars) before dispatch
 *  - systemPrompt truncated to 80 tokens (~320 chars) before dispatch
 *  - No audio, no transcript history, no user identity is transmitted
 *
 * Fallback is per-suggestion: the next question will attempt cloud again.
 *
 * @param geminiProvider        Injectable Gemini streaming provider (default: [GeminiInferenceClient])
 * @param claudeProviderFactory Factory that receives the decrypted API key and returns a Claude provider
 * @param clock                 Injectable clock for deterministic TTFT measurement in tests
 * @param connectivityChecker   Injectable network check to avoid 5s timeout when offline
 */
class CloudInferenceEngine(
    private val context: Context,
    private val apiKeyStore: ApiKeyStore,
    private val configRepository: CloudProviderConfigRepository,
    private val geminiProvider: CloudStreamingProvider,
    private val onDeviceFallback: OnDeviceFallback,
    /**
     * Factory for creating a per-request Claude provider with the decrypted API key.
     * Defaults to [ClaudeInferenceClient] in production; injectable in tests via constructor.
     * Not a constructor parameter in Hilt — [InferenceModule] uses the default directly.
     */
    private val claudeProviderFactory: (apiKey: String) -> CloudStreamingProvider =
        { apiKey -> ClaudeInferenceClient(apiKey = apiKey) },
    private val clock: Clock = SystemClock,
    private val connectivityChecker: (() -> Boolean)? = null
) {

    /** Functional interface — calls the existing on-device SuggestionEngine */
    fun interface OnDeviceFallback {
        fun generate(questionText: String): Flow<InferenceEvent>
    }

    companion object {
        private const val MAX_QUESTION_CHARS = 600  // ≈150 tokens
        private const val MAX_SYSTEM_CHARS = 320    // ≈80 tokens
        private const val CLOUD_TIMEOUT_MS = 5_000L
        private const val TAG = "CloudInferenceEngine"
    }

    /**
     * Streams a suggestion for [request].
     *
     * Emits [InferenceEvent.Token] fragments followed by [InferenceEvent.Complete],
     * or [InferenceEvent.FallbackActivated] + on-device tokens on any cloud failure.
     */
    fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent> = flow {
        // T037: Fallback is PER-SUGGESTION, not per-session.
        // A fallback on this call does NOT disable cloud for the next question.
        // The next call to streamSuggestion() will attempt cloud again from the top.
        // Connection status is only updated on AUTH_ERROR (T035) — all other failures
        // are transient and should be retried on the next question.

        // Guard: respect isEnabled — the engine should not run cloud if the user disabled it
        val config = configRepository.observe(request.provider).first()
        if (!config.isEnabled) {
            emit(InferenceEvent.FallbackActivated(request.requestId, FallbackReason.CLOUD_DISABLED))
            onDeviceFallback.generate(request.questionText).collect { emit(it) }
            return@flow
        }

        // T034: Check connectivity before wasting the 5s timeout
        val networkAvailable = connectivityChecker?.invoke() ?: isNetworkAvailable()
        if (!networkAvailable) {
            emit(InferenceEvent.FallbackActivated(request.requestId, FallbackReason.NETWORK_UNAVAILABLE))
            onDeviceFallback.generate(request.questionText).collect { emit(it) }
            return@flow
        }

        // Retrieve decrypted API key (ByteArray zeroed after use by TinkApiKeyStore)
        val rawKey = apiKeyStore.getKey(request.provider)
        if (rawKey == null) {
            emit(InferenceEvent.FallbackActivated(request.requestId, FallbackReason.CLOUD_DISABLED))
            onDeviceFallback.generate(request.questionText).collect { emit(it) }
            return@flow
        }

        val apiKey = try {
            String(rawKey)
        } finally {
            rawKey.fill(0) // zero plaintext immediately after String copy
        }

        // Apply data minimisation limits (constitution v2.0)
        val safeRequest = request.copy(
            questionText = request.questionText.take(MAX_QUESTION_CHARS),
            systemPrompt = request.systemPrompt.take(MAX_SYSTEM_CHARS)
        )

        // T043: TTFT logging — injectable clock for deterministic test assertions
        val requestStartMs = clock.nowMs()
        var firstTokenMs = -1L

        try {
            withTimeout(CLOUD_TIMEOUT_MS) {
                val providerFlow = when (request.provider) {
                    // Gemini: API key is in google-services.json, not passed in code
                    CloudProvider.GEMINI -> geminiProvider.streamSuggestion(safeRequest)
                    // Claude: API key passed via factory to keep it out of this class
                    CloudProvider.CLAUDE -> claudeProviderFactory(apiKey).streamSuggestion(safeRequest)
                }
                providerFlow.collect { event ->
                    if (event is InferenceEvent.Token && firstTokenMs < 0) {
                        firstTokenMs = clock.nowMs() - requestStartMs
                        Log.d(TAG, "TTFT[${request.provider}] = ${firstTokenMs}ms")
                    }
                    emit(event)
                }
            }
        } catch (e: TimeoutCancellationException) {
            emit(InferenceEvent.FallbackActivated(request.requestId, FallbackReason.TIMEOUT))
            onDeviceFallback.generate(request.questionText).collect { emit(it) }
        } catch (e: CancellationException) {
            throw e // propagate coroutine cancellation
        } catch (e: Exception) {
            val reason = classifyError(e)
            if (reason == FallbackReason.AUTH_ERROR) {
                // T035: auto-disable on 401/403 — update connection status
                configRepository.updateConnectionStatus(request.provider, connected = false)
            }
            emit(InferenceEvent.FallbackActivated(request.requestId, reason))
            onDeviceFallback.generate(request.questionText).collect { emit(it) }
        }
    }

    // T034: network availability check — avoids 5s wait when offline
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun classifyError(e: Exception): FallbackReason {
        val msg = e.message?.lowercase() ?: ""
        return when {
            "401" in msg || "403" in msg || "authentication" in msg || "api key" in msg ->
                FallbackReason.AUTH_ERROR
            "network" in msg || "connect" in msg || "unreachable" in msg ->
                FallbackReason.NETWORK_UNAVAILABLE
            else -> FallbackReason.PROVIDER_ERROR
        }
    }
}
