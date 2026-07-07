package com.meetmind.assistant.di

import com.meetmind.assistant.domain.provider.ResourceProvider
import com.meetmind.assistant.domain.repository.LlmRepository
import com.meetmind.assistant.domain.repository.SettingsRepository
import com.meetmind.assistant.domain.repository.SttRepository
import com.meetmind.assistant.domain.service.RecordingServiceController
import com.meetmind.assistant.domain.usecase.llm.AnalyzePhotoUseCase
import com.meetmind.assistant.domain.usecase.llm.InitializeLlmUseCase
import com.meetmind.assistant.domain.usecase.stt.StartSttStreamingUseCase
import com.meetmind.assistant.domain.usecase.stt.StopSttStreamingUseCase
import com.meetmind.assistant.domain.usecase.sync.SyncSttLlmUseCase
import com.meetmind.assistant.service.RecordingServiceControllerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing domain layer use cases and services.
 * Changed to SingletonComponent to support service injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideSyncSttLlmUseCase(
        llmRepository: LlmRepository,
        settingsRepository: SettingsRepository,
        resourceProvider: ResourceProvider
    ): SyncSttLlmUseCase {
        return SyncSttLlmUseCase(llmRepository, settingsRepository, resourceProvider)
    }

    @Provides
    fun provideStartSttStreamingUseCase(
        sttRepository: SttRepository
    ): StartSttStreamingUseCase {
        return StartSttStreamingUseCase(sttRepository)
    }

    @Provides
    fun provideStopSttStreamingUseCase(
        sttRepository: SttRepository
    ): StopSttStreamingUseCase {
        return StopSttStreamingUseCase(sttRepository)
    }

    @Provides
    fun provideInitializeLlmUseCase(
        llmRepository: LlmRepository,
        settingsRepository: SettingsRepository
    ): InitializeLlmUseCase {
        return InitializeLlmUseCase(llmRepository, settingsRepository)
    }

    @Provides
    fun provideAnalyzePhotoUseCase(
        llmRepository: LlmRepository
    ): AnalyzePhotoUseCase {
        return AnalyzePhotoUseCase(llmRepository)
    }

    @Provides
    @Singleton
    fun provideRecordingServiceController(
        impl: RecordingServiceControllerImpl
    ): RecordingServiceController {
        return impl
    }

    @Provides
    @Singleton
    fun provideDiarizationNotifier(
        impl: com.meetmind.assistant.notification.AndroidDiarizationNotifier
    ): com.meetmind.assistant.domain.notification.DiarizationNotifier {
        return impl
    }
}
