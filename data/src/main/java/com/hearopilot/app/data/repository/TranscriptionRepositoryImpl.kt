package com.hearopilot.app.data.repository

import com.hearopilot.app.data.database.dao.ActionItemDao
import com.hearopilot.app.data.database.dao.LlmInsightDao
import com.hearopilot.app.data.database.dao.SearchDao
import com.hearopilot.app.data.database.dao.TranscriptionSegmentDao
import com.hearopilot.app.data.database.dao.TranscriptionSessionDao
import com.hearopilot.app.data.database.entity.ActionItemEntity
import com.hearopilot.app.data.database.mapper.toDomain
import com.hearopilot.app.data.database.mapper.toEntity
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SearchMatchSource
import com.hearopilot.app.domain.model.SearchResult
import com.hearopilot.app.domain.model.SessionWithDetails
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.model.TranscriptionSession
import com.hearopilot.app.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Implementation of TranscriptionRepository using Room database.
 *
 * Handles conversion between domain models and Room entities,
 * manages session lifecycle and updates lastModifiedAt timestamps.
 *
 * @property sessionDao DAO for session operations
 * @property segmentDao DAO for segment operations
 * @property insightDao DAO for insight operations
 */
class TranscriptionRepositoryImpl @Inject constructor(
    private val sessionDao: TranscriptionSessionDao,
    private val segmentDao: TranscriptionSegmentDao,
    private val insightDao: LlmInsightDao,
    private val searchDao: SearchDao,
    private val actionItemDao: ActionItemDao
) : TranscriptionRepository {

    // ========== Session Management ==========

    override suspend fun createSession(
        name: String?,
        mode: RecordingMode,
        inputLanguage: String,
        outputLanguage: String?,
        insightStrategy: InsightStrategy,
        topic: String?
    ): Result<TranscriptionSession> {
        return try {
            val now = System.currentTimeMillis()
            val finalName = if (name.isNullOrBlank()) {
                val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
                dateFormat.format(Date(now))
            } else {
                name
            }

            val session = TranscriptionSession(
                id = UUID.randomUUID().toString(),
                name = finalName,
                createdAt = now,
                lastModifiedAt = now,
                mode = mode,
                inputLanguage = inputLanguage,
                outputLanguage = outputLanguage,
                insightStrategy = insightStrategy,
                topic = topic?.takeIf { it.isNotBlank() }
            )
            sessionDao.insert(session.toEntity())
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAllSessions(): Flow<List<TranscriptionSession>> {
        return sessionDao.getAllSessions()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getSession(sessionId: String): Flow<TranscriptionSession?> {
        return sessionDao.getSession(sessionId)
            .map { it?.toDomain() }
    }

    override fun getSessionWithDetails(sessionId: String): Flow<SessionWithDetails?> {
        return combine(
            sessionDao.getSession(sessionId),
            segmentDao.getSegmentsBySession(sessionId),
            insightDao.getInsightsBySession(sessionId)
        ) { sessionEntity, segmentEntities, insightEntities ->
            sessionEntity?.let { session ->
                SessionWithDetails(
                    session = session.toDomain(),
                    segments = segmentEntities.map { it.toDomain() },
                    insights = insightEntities.map { it.toDomain() }
                )
            }
        }
    }

    override suspend fun renameSession(sessionId: String, newName: String?): Result<Unit> {
        return try {
            val trimmedName = newName?.trim()?.takeIf { it.isNotBlank() }
            sessionDao.updateName(sessionId, trimmedName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSessionDuration(sessionId: String, durationMs: Long): Result<Unit> {
        return try {
            sessionDao.updateDuration(sessionId, durationMs)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            sessionDao.deleteSession(sessionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllSessions(): Result<Unit> {
        return try {
            sessionDao.deleteAllSessions()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== Segment Management ==========

    override suspend fun saveSegment(segment: TranscriptionSegment): Result<Unit> {
        return try {
            // Save the segment
            segmentDao.insert(segment.toEntity())

            // Update session's lastModifiedAt
            updateSessionModifiedTime(segment.sessionId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSegmentText(segmentId: String, newText: String): Result<Unit> {
        return try {
            segmentDao.updateText(segmentId, newText.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSegmentSpeaker(segmentId: String, speaker: String?): Result<Unit> {
        return try {
            segmentDao.updateSpeaker(segmentId, speaker)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSegmentsBySession(sessionId: String): Flow<List<TranscriptionSegment>> {
        return segmentDao.getSegmentsBySession(sessionId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    // ========== Insight Management ==========

    override suspend fun saveInsight(insight: LlmInsight): Result<Unit> {
        return try {
            // Save the insight
            insightDao.insert(insight.toEntity())

            // Sync action items from insight tasks JSON
            syncActionItemsFromInsight(insight)

            // Update session's lastModifiedAt
            updateSessionModifiedTime(insight.sessionId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses the tasks JSON from an insight and upserts an [ActionItemEntity] for each task.
     * Existing action items for this insight are replaced (via REPLACE conflict strategy).
     *
     * Supports two JSON formats:
     *   - String array: ["task 1", "task 2"]
     *   - Object array: [{"description": "task 1"}, ...]
     */
    private suspend fun syncActionItemsFromInsight(insight: LlmInsight) {
        val tasksJson = insight.tasks ?: return
        try {
            // Delete old items for this insight before re-syncing (handles edit/overwrite)
            actionItemDao.deleteByInsight(insight.id)

            val array = org.json.JSONArray(tasksJson)
            for (i in 0 until array.length()) {
                val text = when (val item = array.get(i)) {
                    is org.json.JSONObject -> item.optString("description", "")
                    else -> item.toString()
                }.trim()
                if (text.isBlank()) continue
                actionItemDao.insert(
                    ActionItemEntity(
                        id = "${insight.id}_$i",
                        sessionId = insight.sessionId,
                        insightId = insight.id,
                        text = text,
                        isDone = false,
                        createdAt = insight.timestamp
                    )
                )
            }
        } catch (_: Exception) {
            // Non-fatal: tasks JSON malformed, skip sync
        }
    }

    override suspend fun updateInsightContent(insightId: String, newContent: String): Result<Unit> {
        return try {
            insightDao.updateContent(insightId, newContent.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateInsightTasks(insightId: String, newTasks: String?): Result<Unit> {
        return try {
            insightDao.updateTasks(insightId, newTasks)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateInsightTitle(insightId: String, newTitle: String?): Result<Unit> {
        return try {
            insightDao.updateTitle(insightId, newTitle?.trim()?.ifBlank { null })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getInsightsBySession(sessionId: String): Flow<List<LlmInsight>> {
        return insightDao.getInsightsBySession(sessionId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    // ========== Statistics ==========

    override fun getTotalDataSizeBytes(): Flow<Long> =
        combine(
            segmentDao.getTotalTextBytes(),
            insightDao.getTotalContentBytes()
        ) { segBytes, insBytes -> segBytes + insBytes }

    // ========== Search ==========

    override fun searchTranscriptions(query: String): Flow<List<SearchResult>> {
        return combine(
            searchDao.searchSegments(query),
            searchDao.searchInsights(query),
            searchDao.searchSessionNames(query),
            searchDao.searchActionItems(query)
        ) { segments, insights, sessionNames, actionItems ->
            val results = mutableListOf<SearchResult>()

            segments.forEach { row ->
                results += SearchResult(
                    sessionId = row.sessionId,
                    sessionName = row.sessionName,
                    mode = runCatching { RecordingMode.valueOf(row.mode) }.getOrDefault(RecordingMode.SIMPLE_LISTENING),
                    createdAt = row.createdAt,
                    snippet = extractSnippet(row.snippetText, query),
                    matchQuery = query,
                    matchSource = SearchMatchSource.TRANSCRIPTION,
                    highlightId = row.id
                )
            }

            insights.forEach { row ->
                results += SearchResult(
                    sessionId = row.sessionId,
                    sessionName = row.sessionName,
                    mode = runCatching { RecordingMode.valueOf(row.mode) }.getOrDefault(RecordingMode.SIMPLE_LISTENING),
                    createdAt = row.createdAt,
                    snippet = extractSnippet(row.snippetText, query),
                    matchQuery = query,
                    matchSource = SearchMatchSource.INSIGHT,
                    highlightId = row.id
                )
            }

            sessionNames.forEach { row ->
                results += SearchResult(
                    sessionId = row.sessionId,
                    sessionName = row.sessionName,
                    mode = runCatching { RecordingMode.valueOf(row.mode) }.getOrDefault(RecordingMode.SIMPLE_LISTENING),
                    createdAt = row.createdAt,
                    snippet = row.sessionName ?: "",
                    matchQuery = query,
                    matchSource = SearchMatchSource.SESSION_NAME
                )
            }

            actionItems.forEach { row ->
                results += SearchResult(
                    sessionId = row.sessionId,
                    sessionName = row.sessionName,
                    mode = runCatching { RecordingMode.valueOf(row.mode) }.getOrDefault(RecordingMode.SIMPLE_LISTENING),
                    createdAt = row.createdAt,
                    snippet = extractSnippet(row.snippetText, query),
                    matchQuery = query,
                    matchSource = SearchMatchSource.ACTION_ITEM,
                    highlightId = row.id
                )
            }

            results.sortedByDescending { it.createdAt }
        }
    }

    /**
     * Extract a ~120-character snippet centred on the first occurrence of [query] in [text].
     * Falls back to the first 120 characters when the query is not found.
     */
    private fun extractSnippet(text: String, query: String): String {
        // Window half-width on each side of the match centre
        val halfWindow = 60
        val idx = text.indexOf(query, ignoreCase = true)
        if (idx < 0) return text.take(120)
        val start = maxOf(0, idx - halfWindow)
        val end = minOf(text.length, idx + query.length + halfWindow)
        val raw = text.substring(start, end)
        // Trim to word boundaries
        val trimmed = if (start > 0) raw.dropWhile { it != ' ' }.trimStart() else raw
        return if (end < text.length) trimmed.dropLastWhile { it != ' ' }.trimEnd() else trimmed
    }

    // ========== Helper Functions ==========

    /**
     * Update the lastModifiedAt timestamp of a session.
     *
     * Called when segments or insights are added to keep the session timestamp current.
     */
    private suspend fun updateSessionModifiedTime(sessionId: String) {
        try {
            val session = sessionDao.getSession(sessionId).first()
            session?.let {
                val updated = it.copy(lastModifiedAt = System.currentTimeMillis())
                sessionDao.update(updated)
            }
        } catch (e: Exception) {
            // Log but don't fail the operation if timestamp update fails
            println("Failed to update session modified time: ${e.message}")
        }
    }
}
