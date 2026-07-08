package com.meetmind.assistant.data.di

import android.content.Context
import androidx.room.Room
import com.meetmind.assistant.data.database.AppDatabase
import com.meetmind.assistant.data.database.dao.ActionItemDao
import com.meetmind.assistant.data.database.dao.LlmInsightDao
import com.meetmind.assistant.data.database.dao.SearchDao
import com.meetmind.assistant.data.database.dao.SessionGroupDao
import com.meetmind.assistant.data.database.dao.SessionPhotoDao
import com.meetmind.assistant.data.database.dao.TranscriptionSegmentDao
import com.meetmind.assistant.data.database.dao.TranscriptionSessionDao
import com.meetmind.assistant.data.repository.ActionItemRepositoryImpl
import com.meetmind.assistant.data.repository.GroupRepositoryImpl
import com.meetmind.assistant.data.repository.TranscriptionRepositoryImpl
import com.meetmind.assistant.domain.repository.ActionItemRepository
import com.meetmind.assistant.domain.repository.GroupRepository
import com.meetmind.assistant.domain.repository.TranscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideTranscriptionSessionDao(database: AppDatabase): TranscriptionSessionDao =
        database.transcriptionSessionDao()

    @Provides
    @Singleton
    fun provideTranscriptionSegmentDao(database: AppDatabase): TranscriptionSegmentDao =
        database.transcriptionSegmentDao()

    @Provides
    @Singleton
    fun provideLlmInsightDao(database: AppDatabase): LlmInsightDao =
        database.llmInsightDao()

    @Provides
    @Singleton
    fun provideSearchDao(database: AppDatabase): SearchDao =
        database.searchDao()

    @Provides
    @Singleton
    fun provideActionItemDao(database: AppDatabase): ActionItemDao =
        database.actionItemDao()

    @Provides
    @Singleton
    fun provideSessionPhotoDao(database: AppDatabase): SessionPhotoDao =
        database.sessionPhotoDao()

    @Provides
    @Singleton
    fun provideSessionGroupDao(database: AppDatabase): SessionGroupDao =
        database.sessionGroupDao()

    @Provides
    @Singleton
    fun provideActionItemRepository(dao: ActionItemDao): ActionItemRepository =
        ActionItemRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideGroupRepository(dao: SessionGroupDao): GroupRepository =
        GroupRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideTranscriptionRepository(
        sessionDao: TranscriptionSessionDao,
        segmentDao: TranscriptionSegmentDao,
        insightDao: LlmInsightDao,
        searchDao: SearchDao,
        actionItemDao: ActionItemDao,
        sessionPhotoDao: SessionPhotoDao,
        audioStorage: com.meetmind.assistant.domain.audio.AudioStorage
    ): TranscriptionRepository {
        return TranscriptionRepositoryImpl(
            sessionDao = sessionDao,
            segmentDao = segmentDao,
            insightDao = insightDao,
            searchDao = searchDao,
            actionItemDao = actionItemDao,
            sessionPhotoDao = sessionPhotoDao,
            audioStorage = audioStorage
        )
    }
}
