package com.meetmind.assistant.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meetmind.assistant.data.database.entity.TranscriptionSegmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for transcription segment operations.
 *
 * Provides operations for managing segments within sessions.
 */
@Dao
interface TranscriptionSegmentDao {

    /**
     * Insert a new segment.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: TranscriptionSegmentEntity)

    /**
     * Get all segments for a specific session ordered by timestamp.
     */
    @Query("SELECT * FROM transcription_segments WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getSegmentsBySession(sessionId: String): Flow<List<TranscriptionSegmentEntity>>

    /**
     * Get count of complete segments in a session.
     */
    @Query("SELECT COUNT(*) FROM transcription_segments WHERE session_id = :sessionId AND is_complete = 1")
    suspend fun getCompleteSegmentCount(sessionId: String): Int

    /**
     * Update the text of an existing segment.
     */
    @Query("UPDATE transcription_segments SET text = :newText WHERE id = :segmentId")
    suspend fun updateText(segmentId: String, newText: String)

    /**
     * Update the speaker label of an existing segment. Null clears the label.
     */
    @Query("UPDATE transcription_segments SET speaker = :speaker WHERE id = :segmentId")
    suspend fun updateSpeaker(segmentId: String, speaker: String?)

    /**
     * Set the diarization cluster id on a segment. Null clears it (used when
     * re-running diarization or when the user manually rejects clusters).
     */
    @Query("UPDATE transcription_segments SET speaker_cluster = :cluster WHERE id = :segmentId")
    suspend fun updateSpeakerCluster(segmentId: String, cluster: Int?)

    /**
     * Propagate a manual speaker label to every segment of [sessionId] that
     * shares the given diarization cluster id. Powers cluster-aware label
     * propagation: tagging one segment as "Boss" relabels every segment the
     * model placed in the same speaker bucket.
     */
    @Query("UPDATE transcription_segments SET speaker = :label WHERE session_id = :sessionId AND speaker_cluster = :cluster")
    suspend fun updateSpeakerByCluster(sessionId: String, cluster: Int, label: String?)

    /**
     * Apply a default cluster label only to segments that don't already have a
     * manually-assigned speaker. Used by the diarization pipeline to seed
     * "Speaker 1", "Speaker 2", … so the transcript is immediately readable
     * without clobbering anything the user already tagged before re-running.
     */
    @Query("UPDATE transcription_segments SET speaker = :label WHERE session_id = :sessionId AND speaker_cluster = :cluster AND speaker IS NULL")
    suspend fun applyDefaultSpeakerLabelByCluster(sessionId: String, cluster: Int, label: String)

    /**
     * Fetch all segments for a session, suspending one-shot. Used by the
     * diarization pipeline which needs to align spans to segments without
     * subscribing to the live Flow.
     */
    @Query("SELECT * FROM transcription_segments WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getSegmentsBySessionOnce(sessionId: String): List<TranscriptionSegmentEntity>

    /**
     * Delete all segments for a session (usually handled by CASCADE).
     */
    @Query("DELETE FROM transcription_segments WHERE session_id = :sessionId")
    suspend fun deleteSegmentsBySession(sessionId: String)

    /**
     * Sum of all segment text lengths across all sessions, in bytes.
     * Used to compute total on-device transcript storage.
     */
    @Query("SELECT COALESCE(SUM(LENGTH(text)), 0) FROM transcription_segments")
    fun getTotalTextBytes(): Flow<Long>
}
