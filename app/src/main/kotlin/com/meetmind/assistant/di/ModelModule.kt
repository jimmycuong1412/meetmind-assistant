// spec 009 — T017 + T032: Hilt module for model config, device detection, and download manager
package com.meetmind.assistant.di

import android.content.Context
import com.meetmind.assistant.data.DeviceTierDetector
import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.data.ModelDownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides application-scoped model configuration singletons:
 *  - [ModelConfigRepository] (on-device model path, threads, GPU layers, variant)
 *  - [DeviceTierDetector] (RAM + SDK → recommended GemmaModelVariant)
 *  - [ModelDownloadManager] (HTTP download with Range-header resume)
 *
 * spec 009 — T017 + T032
 */
@Module
@InstallIn(SingletonComponent::class)
object ModelModule {

    @Provides
    @Singleton
    fun provideModelConfigRepository(
        @ApplicationContext context: Context
    ): ModelConfigRepository = ModelConfigRepository(context)

    @Provides
    @Singleton
    fun provideDeviceTierDetector(): DeviceTierDetector = DeviceTierDetector()

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context
    ): ModelDownloadManager = ModelDownloadManager(context)
}
