package com.hearopilot.app.di

import android.content.Context
import com.arm.aichat.InferenceEngine
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.hearopilot.app.ui.SimulateStreamingAsr
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.service.RecordingNotificationManager
import com.hearopilot.app.service.RecordingSessionManager
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {


    @Provides
    fun provideVad(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): Vad {
        // Read VAD parameters from settings
        val settings = runBlocking { settingsRepository.getSettings().first() }

        // Initialize using existing SimulateStreamingAsr logic with custom parameters
        SimulateStreamingAsr.initVad(
            assetManager = context.assets,
            minSilenceDuration = settings.vadMinSilenceDuration,
            maxSpeechDuration = settings.vadMaxSpeechDuration,
            threshold = settings.vadThreshold
        )
        return SimulateStreamingAsr.vad
    }

    @Provides
    @Singleton
    fun provideInferenceEngine(
        @ApplicationContext context: Context
    ): InferenceEngine {
        return com.arm.aichat.AiChat.getInferenceEngine(context)
    }

    @Provides
    @Singleton
    fun provideRecordingSessionManager(): RecordingSessionManager {
        return RecordingSessionManager()
    }

    @Provides
    @Singleton
    fun provideRecordingNotificationManager(
        @ApplicationContext context: Context
    ): RecordingNotificationManager {
        return RecordingNotificationManager(context)
    }
}
