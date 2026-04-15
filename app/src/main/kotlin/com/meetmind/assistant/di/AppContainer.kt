// T015: Manual DI container — single source of truth for shared singleton dependencies
package com.meetmind.assistant.di

import android.content.Context
import com.meetmind.assistant.inference.ClaudeInferenceClient
import com.meetmind.assistant.inference.CloudBadgeController
import com.meetmind.assistant.inference.CloudInferenceEngine
import com.meetmind.assistant.inference.CloudKeyValidationService
import com.meetmind.assistant.inference.GeminiInferenceClient
import com.meetmind.assistant.storage.ApiKeyStore
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import com.meetmind.assistant.storage.TinkApiKeyStore
import kotlinx.coroutines.flow.flowOf

/**
 * Holds lazily-initialised application-scoped singletons.
 *
 * Accessed via [MeetMindApplication.container]. No Hilt/Dagger to keep
 * the build simple and APK-only per spec 001 (no backend dependencies).
 */
class AppContainer(applicationContext: Context) {

    val apiKeyStore: ApiKeyStore by lazy {
        TinkApiKeyStore(applicationContext)
    }

    val cloudProviderConfigRepository: CloudProviderConfigRepository by lazy {
        CloudProviderConfigRepository(applicationContext, apiKeyStore)
    }

    val cloudKeyValidationService: CloudKeyValidationService by lazy {
        CloudKeyValidationService()
    }

    val geminiInferenceClient: GeminiInferenceClient by lazy {
        GeminiInferenceClient()
    }

    val claudeInferenceClient: ClaudeInferenceClient by lazy {
        ClaudeInferenceClient()
    }

    val cloudInferenceEngine: CloudInferenceEngine by lazy {
        CloudInferenceEngine(
            context = applicationContext,
            apiKeyStore = apiKeyStore,
            configRepository = cloudProviderConfigRepository,
            geminiClient = geminiInferenceClient,
            claudeClient = claudeInferenceClient,
            // On-device fallback: placeholder until spec 004 SuggestionEngine is wired in
            onDeviceFallback = { questionText ->
                flowOf(
                    com.meetmind.assistant.data.model.InferenceEvent.Complete(
                        requestId = java.util.UUID.randomUUID().toString(),
                        fullText = "[On-device fallback — model not loaded]",
                        provider = com.meetmind.assistant.data.model.CloudProvider.GEMINI
                    )
                )
            }
        )
    }
}
