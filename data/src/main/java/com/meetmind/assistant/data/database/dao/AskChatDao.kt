package com.meetmind.assistant.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.meetmind.assistant.data.database.entity.AskChatEntity
import com.meetmind.assistant.data.database.entity.AskChatSessionEntity
import com.meetmind.assistant.data.database.entity.AskChatSummaryRow
import com.meetmind.assistant.data.database.entity.AskMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Ask MeetMind chats. An abstract class (not an interface) so the
 * multi-statement writes can be `@Transaction` methods with a body.
 */
@Dao
abstract class AskChatDao {

    /**
     * Saved chats with their list-row counts and previews. Chats nobody asked anything
     * in are left out: a chat row exists from the moment meetings are picked.
     */
    @Query(
        """
        SELECT c.id AS id,
               c.title AS title,
               c.pinned AS pinned,
               c.created_at AS created_at,
               c.updated_at AS updated_at,
               (SELECT COUNT(*) FROM ask_chat_sessions cs WHERE cs.chat_id = c.id) AS session_count,
               (SELECT COUNT(*) FROM ask_chat_sessions cs
                 WHERE cs.chat_id = c.id
                   AND cs.session_id NOT IN (SELECT t.id FROM transcription_sessions t)) AS missing_session_count,
               (SELECT COUNT(*) FROM ask_messages m WHERE m.chat_id = c.id AND m.role = 'USER') AS question_count,
               (SELECT m.text FROM ask_messages m
                 WHERE m.chat_id = c.id AND m.role = 'USER'
                 ORDER BY m.created_at ASC LIMIT 1) AS first_question,
               (SELECT m.text FROM ask_messages m
                 WHERE m.chat_id = c.id AND m.role = 'ASSISTANT'
                 ORDER BY m.created_at DESC LIMIT 1) AS last_answer,
               (SELECT t.name FROM ask_chat_sessions cs
                 JOIN transcription_sessions t ON t.id = cs.session_id
                 WHERE cs.chat_id = c.id
                 ORDER BY cs.position ASC LIMIT 1) AS first_session_name
        FROM ask_chats c
        WHERE EXISTS (SELECT 1 FROM ask_messages m WHERE m.chat_id = c.id)
        ORDER BY c.pinned DESC, c.updated_at DESC
        """
    )
    abstract fun observeSummaries(): Flow<List<AskChatSummaryRow>>

    @Query("SELECT * FROM ask_chats WHERE id = :chatId")
    abstract fun observeChat(chatId: String): Flow<AskChatEntity?>

    @Query("SELECT session_id FROM ask_chat_sessions WHERE chat_id = :chatId ORDER BY position ASC")
    abstract fun observeSessionIds(chatId: String): Flow<List<String>>

    @Query("SELECT * FROM ask_messages WHERE chat_id = :chatId ORDER BY created_at ASC")
    abstract fun observeMessages(chatId: String): Flow<List<AskMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertChat(chat: AskChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChatSessions(rows: List<AskChatSessionEntity>)

    @Query("DELETE FROM ask_chat_sessions WHERE chat_id = :chatId")
    abstract suspend fun deleteChatSessions(chatId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessage(message: AskMessageEntity)

    @Query("UPDATE ask_chats SET updated_at = :updatedAt WHERE id = :chatId")
    abstract suspend fun touch(chatId: String, updatedAt: Long)

    @Transaction
    open suspend fun createChat(chat: AskChatEntity, sessions: List<AskChatSessionEntity>) {
        insertChat(chat)
        insertChatSessions(sessions)
    }

    @Transaction
    open suspend fun replaceChatSessions(chatId: String, sessions: List<AskChatSessionEntity>, updatedAt: Long) {
        deleteChatSessions(chatId)
        insertChatSessions(sessions)
        touch(chatId, updatedAt)
    }

    @Transaction
    open suspend fun addMessage(message: AskMessageEntity) {
        insertMessage(message)
        touch(message.chatId, message.createdAt)
    }

    @Query("DELETE FROM ask_messages WHERE id = :messageId")
    abstract suspend fun deleteMessage(messageId: String)

    @Query("UPDATE ask_chats SET title = :title WHERE id = :chatId")
    abstract suspend fun renameChat(chatId: String, title: String?)

    @Query("UPDATE ask_chats SET pinned = :pinned WHERE id = :chatId")
    abstract suspend fun setPinned(chatId: String, pinned: Boolean)

    @Query("DELETE FROM ask_chats WHERE id = :chatId")
    abstract suspend fun deleteChat(chatId: String)

    @Query(
        """
        DELETE FROM ask_chats
        WHERE id = :chatId
          AND NOT EXISTS (SELECT 1 FROM ask_messages m WHERE m.chat_id = :chatId)
        """
    )
    abstract suspend fun deleteChatIfEmpty(chatId: String)
}
