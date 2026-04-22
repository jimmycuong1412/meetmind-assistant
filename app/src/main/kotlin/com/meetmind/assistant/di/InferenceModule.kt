// spec 009 — T015: Hilt module for inference singletons
package com.meetmind.assistant.di

import android.content.Context
import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.inference.CloudInferenceEngine
import com.meetmind.assistant.inference.GeminiInferenceClient
import com.meetmind.assistant.inference.OnDeviceLlamaProvider
import com.meetmind.assistant.storage.ApiKeyStore
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides application-scoped inference singletons:
 *  - [GeminiInferenceClient] (Gemini streaming provider)
 *  - [OnDeviceLlamaProvider] (on-device Gemma 4 E4B llama.cpp)
 *  - [CloudInferenceEngine] (cloud routing + on-device fallback)
 *
 * Note: [claudeProviderFactory] is now internal to [CloudInferenceEngine].
 *
 * spec 009 — T015
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideGeminiInferenceClient(): GeminiInferenceClient = GeminiInferenceClient()

    @Provides
    @Singleton
    fun provideOnDeviceLlamaProvider(
        configRepository: ModelConfigRepository
    ): OnDeviceLlamaProvider = OnDeviceLlamaProvider(configRepository)

    @Provides
    @Singleton
    fun provideCloudInferenceEngine(
        @ApplicationContext context: Context,
        apiKeyStore: ApiKeyStore,
        configRepository: CloudProviderConfigRepository,
        geminiInferenceClient: GeminiInferenceClient,
        onDeviceLlamaProvider: OnDeviceLlamaProvider
    ): CloudInferenceEngine = CloudInferenceEngine(
        context = context,
        apiKeyStore = apiKeyStore,
        configRepository = configRepository,
        geminiProvider = geminiInferenceClient,
        onDeviceFallback = onDeviceLlamaProvider
    )
}
