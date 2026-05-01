package com.meetmind.assistant.domain.repository

import com.meetmind.assistant.domain.model.DiarizationStatus
import com.meetmind.assistant.domain.model.InsightStrategy
import com.meetmind.assistant.domain.model.LlmInsight
import com.meetmind.assistant.domain.model.RecordingMode
import com.meetmind.assistant.domain.model.SearchResult
import com.meetmind.assistant.domain.model.SessionWithDetails
import com.meetmind.assistant.domain.model.TranscriptionSegment
import com.meetmind.assistant.domain.model.TranscriptionSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for transcription session persistence.
 *
 * Manages storage and retrieval of transcription sessions along with their
 * associated segments and LLM insights. Each session represents an isolated
 * recording context.
 */
interface TranscriptionRepository {

    // ========== Session Management ==========

    /**
     * Create a new transcription session.
     *
     * @param name Optional user-provided name for the session
     * @param mode Recording mode determining insight frequency and format
     * @param inputLanguage Language being spoken (BCP-47 code)
     * @param outputLanguage Optional target language for translation mode
     * @param insightStrategy Whether insights are generated in real-time or at end of session
     * @param topic Optional main subject/topic for focused AI insights
     * @return Result containing the created session or an error
     */
    suspend fun createSession(
        name: String?,
        mode: RecordingMode = RecordingMode.SIMPLE_LISTENING,
        inputLanguage: String,
        outputLanguage: String? = null,
        insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
        topic: String? = null
    ): Result<TranscriptionSession>

    /**
     * Get all transcription sessions ordered by creation time (most recent first).
     *
     * @return Flow emitting list of all sessions
     */
    fun getAllSessions(): Flow<List<TranscriptionSession>>

    /**
     * Get a specific session by ID.
     *
     * @param sessionId The session ID to retrieve
     * @return Flow emitting the session or null if not found
     */
    fun getSession(sessionId: String): Flow<TranscriptionSession?>

    /**
     * Get a session with all its associated segments and insights.
     *
     * @param sessionId The session ID to retrieve
     * @return Flow emitting session details or null if not found
     */
    fun getSessionWithDetails(sessionId: String): Flow<SessionWithDetails?>

    /**
     * Delete a session and all its associated data (segments and insights).
     *
     * Uses CASCADE delete to ensure data consistency.
     *
     * @param sessionId The session ID to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /**
     * Rename a session.
     *
     * Passing null or blank name clears the name (session becomes unnamed).
     *
     * @param sessionId The session ID to rename
     * @param newName The new name, or null to clear
     * @return Result indicating success or failure
     */
    suspend fun renameSession(sessionId: String, newName: String?): Result<Unit>

    /**
     * Persist the total recording duration for a session.
     *
     * Called once when the recording stops, before the session is closed.
     *
     * @param sessionId The session ID to update
     * @param durationMs Total recording duration in milliseconds
     * @return Result indicating success or failure
     */
    suspend fun updateSessionDuration(sessionId: String, durationMs: Long): Result<Unit>

    /**
     * Persist the path of the WAV recording produced for a session and set its
     * diarization status to [DiarizationStatus.NOT_RUN], indicating that
     * speaker diarization can be run for it on demand.
     *
     * Pass null for [audioFilePath] to clear the path (e.g. after the audio
     * has been auto-deleted post-diarization or the user opted out). Clearing
     * the path also moves the status to [DiarizationStatus.UNAVAILABLE] unless
     * [diarizationStatus] is supplied explicitly.
     */
    suspend fun updateSessionAudioFile(
        sessionId: String,
        audioFilePath: String?,
        diarizationStatus: DiarizationStatus = if (audioFilePath != null) DiarizationStatus.NOT_RUN else DiarizationStatus.UNAVAILABLE
    ): Result<Unit>

    /**
     * Update the diarization status for a session without changing its audio
     * file path. Used during the diarization pipeline lifecycle (RUNNING →
     * COMPLETED / FAILED).
     */
    suspend fun updateSessionDiarizationStatus(
        sessionId: String,
        status: DiarizationStatus
    ): Result<Unit>

    /**
     * Persist a diarization cluster id on a single segment. Used by the
     * diarization pipeline when aligning speaker spans to text segments.
     */
    suspend fun updateSegmentCluster(segmentId: String, cluster: Int?): Result<Unit>

    /**
     * Propagate a manual speaker label to every segment of [sessionId] that
     * shares the given diarization cluster. Implements cluster-aware label
     * propagation: when the user tags one segment in cluster 2 as "Alice",
     * every segment in cluster 2 of the same session inherits that label.
     */
    suspend fun propagateSpeakerLabelByCluster(
        sessionId: String,
        cluster: Int,
        label: String?
    ): Result<Unit>

    /**
     * One-shot snapshot of all segments for [sessionId]. Used by the
     * diarization pipeline which needs ordered segments with audio offsets,
     * but doesn't need (and shouldn't subscribe to) the live Flow.
     */
    suspend fun getSegmentsBySessionOnce(sessionId: String): List<TranscriptionSegment>

    /**
     * Delete all sessions and their associated data.
     *
     * WARNING: This is irreversible.
     *
     * @return Result indicating success or failure
     */
    suspend fun deleteAllSessions(): Result<Unit>

    // ========== Segment Management ==========

    /**
     * Save a transcription segment to the database.
     *
     * Updates the session's lastModifiedAt timestamp.
     *
     * @param segment The segment to save
     * @return Result indicating success or failure
     */
    suspend fun saveSegment(segment: TranscriptionSegment): Result<Unit>

    /**
     * Update the text of an existing transcription segment.
     *
     * @param segmentId The segment ID to update
     * @param newText Replacement text
     * @return Result indicating success or failure
     */
    suspend fun updateSegmentText(segmentId: String, newText: String): Result<Unit>

    /**
     * Assign (or clear) a speaker label on a transcription segment.
     *
     * @param segmentId The segment ID to update
     * @param speaker The speaker label (e.g. "Me", "Person A"), or null to clear
     * @return Result indicating success or failure
     */
    suspend fun updateSegmentSpeaker(segmentId: String, speaker: String?): Result<Unit>

    /**
     * Get all segments for a specific session ordered by timestamp.
     *
     * @param sessionId The session ID
     * @return Flow emitting list of segments
     */
    fun getSegmentsBySession(sessionId: String): Flow<List<TranscriptionSegment>>

    // ========== Insight Management ==========

    /**
     * Save an LLM insight to the database.
     *
     * Updates the session's lastModifiedAt timestamp.
     *
     * @param insight The insight to save
     * @return Result indicating success or failure
     */
    suspend fun saveInsight(insight: LlmInsight): Result<Unit>

    /**
     * Update the content of an existing insight.
     *
     * @param insightId The insight ID to update
     * @param newContent Replacement content text
     * @return Result indicating success or failure
     */
    suspend fun updateInsightContent(insightId: String, newContent: String): Result<Unit>

    /**
     * Update the tasks JSON of an existing insight.
     *
     * @param insightId The insight ID to update
     * @param newTasks Replacement tasks JSON string, or null to clear
     * @return Result indicating success or failure
     */
    suspend fun updateInsightTasks(insightId: String, newTasks: String?): Result<Unit>

    /**
     * Update the title of an existing insight.
     *
     * @param insightId The insight ID to update
     * @param newTitle Replacement title text, or null to clear
     * @return Result indicating success or failure
     */
    suspend fun updateInsightTitle(insightId: String, newTitle: String?): Result<Unit>

    /**
     * Get all insights for a specific session ordered by timestamp.
     *
     * @param sessionId The session ID
     * @return Flow emitting list of insights
     */
    fun getInsightsBySession(sessionId: String): Flow<List<LlmInsight>>

    // ========== Statistics ==========

    /**
     * Total bytes of all transcription and insight text stored on-device.
     *
     * Sums segment text lengths and insight content + tasks lengths across all sessions.
     * Emits 0 when no data is stored.
     */
    fun getTotalDataSizeBytes(): Flow<Long>

    // ========== Search ==========

    /**
     * Full-text search across transcription segments, LLM insights, and session names.
     *
     * Returns results sorted by session creation time (newest first).
     * A session may appear multiple times if the query matches in more than one source.
     *
     * @param query The search string (must be at least 2 characters).
     * @return Flow emitting a list of [SearchResult] ordered by [SearchResult.createdAt] DESC.
     */
    fun searchTranscriptions(query: String): Flow<List<SearchResult>>
}
