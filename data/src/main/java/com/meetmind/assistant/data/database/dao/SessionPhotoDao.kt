package com.meetmind.assistant.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meetmind.assistant.data.database.entity.SessionPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionPhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: SessionPhotoEntity)

    @Query("UPDATE session_photos SET description = :description WHERE id = :photoId")
    suspend fun updateDescription(photoId: String, description: String)

    @Query("SELECT * FROM session_photos WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getPhotosForSession(sessionId: String): Flow<List<SessionPhotoEntity>>
}
