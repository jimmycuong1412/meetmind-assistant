package com.meetmind.assistant.di

import com.meetmind.assistant.domain.service.LlmProcessingServiceController
import com.meetmind.assistant.service.LlmProcessingServiceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding service-layer domain interfaces to their Android implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindLlmProcessingServiceController(
        impl: LlmProcessingServiceControllerImpl
    ): LlmProcessingServiceController
}
