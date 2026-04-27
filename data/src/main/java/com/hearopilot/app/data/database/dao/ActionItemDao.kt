package com.hearopilot.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hearopilot.app.data.database.entity.ActionItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActionItemEntity)

    @Query("SELECT * FROM action_items WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun getBySession(sessionId: String): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE is_done = 0 ORDER BY created_at DESC")
    fun getAllPending(): Flow<List<ActionItemEntity>>

    @Query("UPDATE action_items SET is_done = :isDone WHERE id = :id")
    suspend fun setDone(id: String, isDone: Boolean)

    @Query("DELETE FROM action_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM action_items WHERE insight_id = :insightId")
    suspend fun deleteByInsight(insightId: String)
}
