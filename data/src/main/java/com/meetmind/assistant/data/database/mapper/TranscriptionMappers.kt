package com.meetmind.assistant.data.database.mapper

import com.meetmind.assistant.data.database.entity.ActionItemEntity
import com.meetmind.assistant.data.database.entity.LlmInsightEntity
import com.meetmind.assistant.data.database.entity.TranscriptionSegmentEntity
import com.meetmind.assistant.data.database.entity.TranscriptionSessionEntity
import com.meetmind.assistant.domain.model.ActionItem
import com.meetmind.assistant.domain.model.DiarizationStatus
import com.meetmind.assistant.domain.model.InsightStrategy
import com.meetmind.assistant.domain.model.LlmInsight
import com.meetmind.assistant.domain.model.RecordingMode
import com.meetmind.assistant.domain.model.TranscriptionSegment
import com.meetmind.assistant.domain.model.TranscriptionSession
import org.json.JSONArray

/**
 * Mappers for converting between Room entities and domain models.
 *
 * These extension functions enable clean separation between the data layer
 * (Room entities) and the domain layer (business models).
 */

// ========== Session Mappers ==========

/**
 * Convert Room entity to domain model.
 */
fun TranscriptionSessionEntity.toDomain(): TranscriptionSession {
    return TranscriptionSession(
        id = id,
        name = name,
        createdAt = createdAt,
        lastModifiedAt = lastModifiedAt,
        mode = try {
            RecordingMode.valueOf(mode)
        } catch (e: IllegalArgumentException) {
            RecordingMode.SIMPLE_LISTENING // Safe fallback
        },
        inputLanguage = inputLanguage,
        outputLanguage = outputLanguage,
        durationMs = durationMs,
        insightStrategy = try {
            InsightStrategy.valueOf(insightStrategy)
        } catch (e: IllegalArgumentException) {
            InsightStrategy.REAL_TIME // Safe fallback for legacy rows
        },
        topic = topic,
        audioFilePath = audioFilePath,
        diarizationStatus = try {
            DiarizationStatus.valueOf(diarizationStatus)
        } catch (e: IllegalArgumentException) {
            DiarizationStatus.UNAVAILABLE
        }
    )
}

/**
 * Convert domain model to Room entity.
 */
fun TranscriptionSession.toEntity(): TranscriptionSessionEntity {
    return TranscriptionSessionEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        lastModifiedAt = lastModifiedAt,
        mode = mode.name,
        inputLanguage = inputLanguage,
        outputLanguage = outputLanguage,
        durationMs = durationMs,
        insightStrategy = insightStrategy.name,
        topic = topic,
        audioFilePath = audioFilePath,
        diarizationStatus = diarizationStatus.name
    )
}

// ========== Segment Mappers ==========

/**
 * Convert Room entity to domain model.
 */
fun TranscriptionSegmentEntity.toDomain(): TranscriptionSegment {
    return TranscriptionSegment(
        id = id,
        sessionId = sessionId,
        text = text,
        timestamp = timestamp,
        isComplete = isComplete,
        speaker = speaker,
        speakerCluster = speakerCluster,
        startOffsetMs = startOffsetMs,
        endOffsetMs = endOffsetMs
    )
}

/**
 * Convert domain model to Room entity.
 */
fun TranscriptionSegment.toEntity(): TranscriptionSegmentEntity {
    return TranscriptionSegmentEntity(
        id = id,
        sessionId = sessionId,
        text = text,
        timestamp = timestamp,
        isComplete = isComplete,
        speaker = speaker,
        speakerCluster = speakerCluster,
        startOffsetMs = startOffsetMs,
        endOffsetMs = endOffsetMs
    )
}

// ========== Insight Mappers ==========

/**
 * Convert Room entity to domain model.
 *
 * Parses the JSON string of source segment IDs back to a list.
 */
fun LlmInsightEntity.toDomain(): LlmInsight {
    return LlmInsight(
        id = id,
        sessionId = sessionId,
        title = title,
        content = content,
        tasks = tasks,
        timestamp = timestamp,
        sourceSegmentIds = parseSourceSegmentIds(sourceSegmentIds),
        questionType = questionType
    )
}

/**
 * Convert domain model to Room entity.
 *
 * Serializes the list of source segment IDs to a JSON string.
 */
fun LlmInsight.toEntity(): LlmInsightEntity {
    return LlmInsightEntity(
        id = id,
        sessionId = sessionId,
        title = title,
        content = content,
        tasks = tasks,
        timestamp = timestamp,
        sourceSegmentIds = serializeSourceSegmentIds(sourceSegmentIds),
        questionType = questionType
    )
}

// ========== ActionItem Mappers ==========

fun ActionItemEntity.toDomain(): ActionItem = ActionItem(
    id = id,
    sessionId = sessionId,
    insightId = insightId,
    text = text,
    isDone = isDone,
    createdAt = createdAt
)

fun ActionItem.toEntity(): ActionItemEntity = ActionItemEntity(
    id = id,
    sessionId = sessionId,
    insightId = insightId,
    text = text,
    isDone = isDone,
    createdAt = createdAt
)

// ========== Helper Functions ==========

/**
 * Serialize list of segment IDs to JSON array string.
 *
 * Example: ["id1", "id2", "id3"]
 */
private fun serializeSourceSegmentIds(ids: List<String>): String {
    val jsonArray = JSONArray()
    ids.forEach { jsonArray.put(it) }
    return jsonArray.toString()
}

/**
 * Parse JSON array string to list of segment IDs.
 *
 * Returns empty list if parsing fails.
 */
private fun parseSourceSegmentIds(json: String): List<String> {
    return try {
        val jsonArray = JSONArray(json)
        List(jsonArray.length()) { index ->
            jsonArray.getString(index)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
