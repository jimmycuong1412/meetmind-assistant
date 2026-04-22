// spec 009 — T041: Hilt module for STT pipeline
package com.meetmind.assistant.di

import com.meetmind.assistant.stt.SherpaOnnxDataSource
import com.meetmind.assistant.stt.SttRepository
import com.meetmind.assistant.stt.SttRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides and binds the STT pipeline components.
 *
 * - [SherpaOnnxDataSource] is a `@Singleton` `@Inject constructor` class — Hilt creates it automatically.
 * - [SttRepository] is bound to [SttRepositoryImpl] via `@Binds`.
 *
 * spec 009 — T041
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SttModule {

    /**
     * Bind [SttRepositoryImpl] as the singleton [SttRepository] implementation.
     * [SherpaOnnxDataSource] is injected into [SttRepositoryImpl] by Hilt automatically
     * (it carries `@Singleton @Inject constructor`).
     */
    @Binds
    @Singleton
    abstract fun bindSttRepository(impl: SttRepositoryImpl): SttRepository
}
