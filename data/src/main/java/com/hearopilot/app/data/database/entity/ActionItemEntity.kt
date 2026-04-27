package com.hearopilot.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for action items extracted from LLM insights.
 *
 * Each row represents one task string parsed from [LlmInsightEntity.tasks].
 * Cascade-deletes when the parent session is deleted.
 */
@Entity(
    tableName = "action_items",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptionSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["insight_id"])
    ]
)
data class ActionItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "insight_id")
    val insightId: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
