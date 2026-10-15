package com.meetmind.assistant.data.repository

import com.meetmind.assistant.data.database.dao.AskChatDao
import com.meetmind.assistant.data.database.entity.AskChatEntity
import com.meetmind.assistant.data.database.entity.AskChatSessionEntity
import com.meetmind.assistant.data.database.entity.AskChatSummaryRow
import com.meetmind.assistant.data.database.entity.AskMessageEntity
import com.meetmind.assistant.domain.model.AskChat
import com.meetmind.assistant.domain.model.AskChatSummary
import com.meetmind.assistant.domain.model.AskCitation
import com.meetmind.assistant.domain.model.AskMessage
import com.meetmind.assistant.domain.model.AskRole
import com.meetmind.assistant.domain.repository.AskChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AskChatRepositoryImpl(
    private val dao: AskChatDao
) : AskChatRepository {

    override fun observeChatSummaries(): Flow<List<AskChatSummary>> =
        dao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeChat(chatId: String): Flow<AskChat?> =
        combine(
            dao.observeChat(chatId),
            dao.observeSessionIds(chatId),
            dao.observeMessages(chatId)
        ) { chat, sessionIds, messages ->
            chat?.let {
                AskChat(
                    id = it.id,
                    title = it.title,
                    pinned = it.pinned,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    sessionIds = sessionIds,
                    messages = messages.map { message -> message.toDomain() }
                )
            }
        }

    override suspend fun createChat(sessionIds: List<String>): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.createChat(
            chat = AskChatEntity(id = id, createdAt = now, updatedAt = now),
            sessions = sessionIds.toSessionRows(id)
        )
        return id
    }

    override suspend fun updateChatSessions(chatId: String, sessionIds: List<String>) {
        dao.replaceChatSessions(chatId, sessionIds.toSessionRows(chatId), System.currentTimeMillis())
    }

    override suspend fun addMessage(message: AskMessage) {
        dao.addMessage(message.toEntity())
    }

    override suspend fun deleteMessage(messageId: String) {
        dao.deleteMessage(messageId)
    }

    override suspend fun renameChat(chatId: String, title: String?) {
        dao.renameChat(chatId, title?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) {
        dao.setPinned(chatId, pinned)
    }

    override suspend fun deleteChat(chatId: String) {
        dao.deleteChat(chatId)
    }

    override suspend fun deleteChatIfEmpty(chatId: String) {
        dao.deleteChatIfEmpty(chatId)
    }

    private fun List<String>.toSessionRows(chatId: String) =
        distinct().mapIndexed { position, sessionId ->
            AskChatSessionEntity(chatId = chatId, sessionId = sessionId, position = position)
        }

    private fun AskChatSummaryRow.toDomain() = AskChatSummary(
        id = id,
        title = title,
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sessionCount = sessionCount,
        missingSessionCount = missingSessionCount,
        questionCount = questionCount,
        firstQuestion = firstQuestion,
        lastAnswer = lastAnswer,
        singleSessionName = firstSessionName.takeIf { sessionCount == 1 }
    )

    private fun AskMessageEntity.toDomain() = AskMessage(
        id = id,
        chatId = chatId,
        role = runCatching { AskRole.valueOf(role) }.getOrDefault(AskRole.ASSISTANT),
        text = text,
        createdAt = createdAt,
        citations = citationsJson?.let(::parseCitations).orEmpty(),
        notFound = notFound
    )

    private fun AskMessage.toEntity() = AskMessageEntity(
        id = id,
        chatId = chatId,
        role = role.name,
        text = text,
        createdAt = createdAt,
        citationsJson = citations.takeIf { it.isNotEmpty() }?.let(::citationsToJson),
        notFound = notFound
    )

    private fun citationsToJson(citations: List<AskCitation>): String =
        JSONArray().apply {
            citations.forEach { c ->
                put(
                    JSONObject().apply {
                        put("number", c.number)
                        put("sessionId", c.sessionId)
                        put("sessionName", c.sessionName ?: JSONObject.NULL)
                        put("segmentId", c.segmentId ?: JSONObject.NULL)
                        put("offsetMs", c.offsetMs ?: JSONObject.NULL)
                        put("speaker", c.speaker ?: JSONObject.NULL)
                        put("quote", c.quote)
                    }
                )
            }
        }.toString()

    private fun parseCitations(json: String): List<AskCitation> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AskCitation(
                number = o.getInt("number"),
                sessionId = o.getString("sessionId"),
                sessionName = o.optStringOrNull("sessionName"),
                segmentId = o.optStringOrNull("segmentId"),
                offsetMs = if (o.isNull("offsetMs")) null else o.getLong("offsetMs"),
                speaker = o.optStringOrNull("speaker"),
                quote = o.optString("quote", "")
            )
        }
    }.getOrDefault(emptyList())

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)
}
