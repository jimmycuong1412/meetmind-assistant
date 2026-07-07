package com.meetmind.assistant.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a photo captured during a recording session.
 * Deleted automatically when the parent session is deleted (CASCADE).
 */
@Entity(
    tableName = "session_photos",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptionSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"])
    ]
)
data class SessionPhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
