package com.meetmind.assistant.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single transcription segment.
 *
 * Segments belong to a specific session via foreign key relationship.
 * When a session is deleted, all its segments are automatically deleted (CASCADE).
 */
@Entity(
    tableName = "transcription_segments",
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
data class TranscriptionSegmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_complete")
    val isComplete: Boolean,

    @ColumnInfo(name = "speaker")
    val speaker: String? = null,  // Optional manual speaker label; null = unassigned

    @ColumnInfo(name = "speaker_cluster")
    val speakerCluster: Int? = null,  // Diarization cluster id; null until diarization runs

    @ColumnInfo(name = "start_offset_ms")
    val startOffsetMs: Long? = null,  // Audio offset of segment start, ms from session start

    @ColumnInfo(name = "end_offset_ms")
    val endOffsetMs: Long? = null  // Audio offset of segment end, ms from session start
)
