package com.meetmind.assistant.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meetmind.assistant.data.database.entity.SessionGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: SessionGroupEntity)

    @Query("SELECT * FROM session_groups ORDER BY created_at ASC")
    fun getAllGroups(): Flow<List<SessionGroupEntity>>

    @Query("DELETE FROM session_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("UPDATE transcription_sessions SET group_id = :groupId WHERE id = :sessionId")
    suspend fun setSessionGroup(sessionId: String, groupId: String?)

    @Query("UPDATE transcription_sessions SET group_id = NULL WHERE group_id = :groupId")
    suspend fun clearGroupFromSessions(groupId: String)
}
