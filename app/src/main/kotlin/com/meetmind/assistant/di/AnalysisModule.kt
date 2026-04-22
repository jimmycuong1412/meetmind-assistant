// spec 009 — T016: Hilt module for continuous analysis singletons
package com.meetmind.assistant.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.meetmind.assistant.analysis.AnalysisCadenceController
import com.meetmind.assistant.analysis.ConversationAnalyzer
import com.meetmind.assistant.analysis.DefaultConversationAnalyzer
import com.meetmind.assistant.analysis.DuplicateSuppressor
import com.meetmind.assistant.analysis.TranscriptWindowBuffer
import com.meetmind.assistant.data.AnalysisSettingsRepository
import com.meetmind.assistant.inference.GeminiInferenceClient
import com.meetmind.assistant.inference.OnDeviceLlamaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the analysis DataStore (name = "analysis_settings").
 * Distinguishes it from other DataStore<Preferences> instances in the graph.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnalysisDataStore

private val Context.analysisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "analysis_settings"
)

/**
 * Provides application-scoped continuous analysis singletons:
 *  - [AnalysisSettingsRepository] (DataStore-backed analysis settings)
 *  - [TranscriptWindowBuffer] (@Singleton — shared feed from ASR)
 *  - [DefaultConversationAnalyzer] (keyword classify + LLM suggestion)
 *  - [AnalysisCadenceController] (@Singleton — session cadence controller)
 *
 * spec 009 — T016
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalysisModule {

    @Provides
    @AnalysisDataStore
    fun provideAnalysisDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.analysisDataStore

    @Provides
    @Singleton
    fun provideAnalysisSettingsRepository(
        @AnalysisDataStore dataStore: DataStore<Preferences>
    ): AnalysisSettingsRepository = AnalysisSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideTranscriptWindowBuffer(): TranscriptWindowBuffer = TranscriptWindowBuffer()

    @Provides
    @Singleton
    fun provideConversationAnalyzer(
        geminiInferenceClient: GeminiInferenceClient,
        onDeviceLlamaProvider: OnDeviceLlamaProvider
    ): ConversationAnalyzer = DefaultConversationAnalyzer(
        inferenceEngine = geminiInferenceClient,
        duplicateSuppressor = DuplicateSuppressor(),
        isModelLoaded = { onDeviceLlamaProvider.isLoaded() }
    )

    @Provides
    @Singleton
    fun provideAnalysisCadenceController(
        analyzer: ConversationAnalyzer,
        buffer: TranscriptWindowBuffer
    ): AnalysisCadenceController = AnalysisCadenceController(
        analyzer = analyzer,
        buffer = buffer,
        intervalMs = 20_000L,    // default; SessionViewModel overrides at session start
        windowSizeMs = 60_000L
    )
}
