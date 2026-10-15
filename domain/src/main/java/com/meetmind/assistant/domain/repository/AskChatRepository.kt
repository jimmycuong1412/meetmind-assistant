package com.meetmind.assistant.domain.repository

import com.meetmind.assistant.domain.model.AskChat
import com.meetmind.assistant.domain.model.AskChatSummary
import com.meetmind.assistant.domain.model.AskMessage
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for "Ask MeetMind" chats. Everything stays in the on-device database.
 */
interface AskChatRepository {

    /** Chats that have at least one question, pinned first, then most recently updated. */
    fun observeChatSummaries(): Flow<List<AskChatSummary>>

    /** A chat with its session ids and messages (oldest first), or null once deleted. */
    fun observeChat(chatId: String): Flow<AskChat?>

    /** Create an empty chat over [sessionIds] and return its id. */
    suspend fun createChat(sessionIds: List<String>): String

    /** Replace the meetings a chat reads. Earlier answers keep their snapshotted citations. */
    suspend fun updateChatSessions(chatId: String, sessionIds: List<String>)

    suspend fun addMessage(message: AskMessage)

    suspend fun deleteMessage(messageId: String)

    /** Rename a chat; null or blank restores the automatic title (first question). */
    suspend fun renameChat(chatId: String, title: String?)

    suspend fun setPinned(chatId: String, pinned: Boolean)

    suspend fun deleteChat(chatId: String)

    /** Delete [chatId] only if no message was ever added to it. */
    suspend fun deleteChatIfEmpty(chatId: String)
}
