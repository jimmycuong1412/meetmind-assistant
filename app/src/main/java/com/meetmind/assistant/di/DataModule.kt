package com.meetmind.assistant.di

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import com.arm.aichat.InferenceEngine
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import com.meetmind.assistant.data.datasource.LlmDataSource
import com.meetmind.assistant.data.config.DefaultModelConfig
import com.meetmind.assistant.data.config.ModelConfig
import com.meetmind.assistant.data.datasource.ModelDownloadManager
import com.meetmind.assistant.data.datasource.SttDataSource
import com.meetmind.assistant.data.monitor.AndroidThermalMonitor
import com.meetmind.assistant.data.provider.AndroidResourceProvider
import com.meetmind.assistant.data.repository.LlmRepositoryImpl
import com.meetmind.assistant.data.repository.SettingsRepositoryImpl
import com.meetmind.assistant.data.repository.SttRepositoryImpl
import com.meetmind.assistant.domain.monitor.ThermalMonitor
import com.meetmind.assistant.domain.provider.ResourceProvider
import com.meetmind.assistant.domain.repository.LlmRepository
import com.meetmind.assistant.domain.repository.SettingsRepository
import com.meetmind.assistant.domain.repository.SttRepository
import com.meetmind.assistant.feature.llm.datasource.LlamaAndroidDataSource
import com.meetmind.assistant.feature.stt.datasource.SherpaOnnxDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing data layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSttDataSource(
        @ApplicationContext context: Context,
        vadProvider: javax.inject.Provider<Vad>
    ): SttDataSource {
        val audioManager = context.getSystemService(AudioManager::class.java)
        return SherpaOnnxDataSource(context, vadProvider, audioManager)
    }

    @Provides
    @Singleton
    fun provideLlmDataSource(
        inferenceEngine: InferenceEngine
    ): LlmDataSource {
        return LlamaAndroidDataSource(inferenceEngine)
    }

    @Provides
    @Singleton
    fun provideSttRepository(
        dataSource: SttDataSource
    ): SttRepository {
        return SttRepositoryImpl(dataSource)
    }

    @Provides
    @Singleton
    fun provideLlmRepository(
        dataSource: LlmDataSource,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): LlmRepository {
        return LlmRepositoryImpl(dataSource, settingsRepository, context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        resourceProvider: ResourceProvider
    ): SettingsRepository {
        return SettingsRepositoryImpl(context, resourceProvider)
    }

    @Provides
    @Singleton
    fun provideModelConfig(): ModelConfig = DefaultModelConfig.INSTANCE

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        modelConfig: ModelConfig
    ): ModelDownloadManager {
        return ModelDownloadManager(context, modelConfig)
    }

    @Provides
    @Singleton
    fun provideResourceProvider(
        @ApplicationContext context: Context
    ): ResourceProvider {
        return AndroidResourceProvider(context)
    }

    @Provides
    @Singleton
    fun provideThermalMonitor(
        @ApplicationContext context: Context
    ): ThermalMonitor {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return AndroidThermalMonitor(powerManager)
    }
}
