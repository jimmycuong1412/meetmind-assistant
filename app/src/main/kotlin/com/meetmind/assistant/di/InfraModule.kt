// spec 009 — T014: Hilt module for infrastructure singletons
package com.meetmind.assistant.di

import android.content.Context
import com.meetmind.assistant.inference.CloudKeyValidationService
import com.meetmind.assistant.storage.ApiKeyStore
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import com.meetmind.assistant.storage.TinkApiKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides application-scoped infrastructure singletons:
 *  - [ApiKeyStore] (Tink-encrypted key storage)
 *  - [CloudProviderConfigRepository] (provider selection + enabled state)
 *  - [CloudKeyValidationService] (network validation against Gemini/Claude)
 *
 * DataStore instances are created via the `preferencesDataStore` delegates defined in each
 * repository's companion/file scope — the repositories own their DataStore construction.
 *
 * spec 009 — T014
 */
@Module
@InstallIn(SingletonComponent::class)
object InfraModule {

    @Provides
    @Singleton
    fun provideApiKeyStore(
        @ApplicationContext context: Context
    ): ApiKeyStore = TinkApiKeyStore(context)

    @Provides
    @Singleton
    fun provideCloudProviderConfigRepository(
        @ApplicationContext context: Context,
        apiKeyStore: ApiKeyStore
    ): CloudProviderConfigRepository = CloudProviderConfigRepository(context, apiKeyStore)

    @Provides
    @Singleton
    fun provideCloudKeyValidationService(): CloudKeyValidationService =
        CloudKeyValidationService()
}
