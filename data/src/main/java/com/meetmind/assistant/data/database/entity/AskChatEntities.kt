package com.meetmind.assistant.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a saved "Ask MeetMind" chat.
 *
 * @property title User-chosen name; null means "use the first question".
 */
@Entity(tableName = "ask_chats")
data class AskChatEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

/**
 * A meeting a chat reads. [sessionId] deliberately has no foreign key to
 * `transcription_sessions`: when a meeting is deleted the row stays, so Saved chats
 * can say "1 of 2 meetings deleted" instead of silently shrinking the chat.
 *
 * @property position Order the user picked the meetings in.
 */
@Entity(
    tableName = "ask_chat_sessions",
    primaryKeys = ["chat_id", "session_id"],
    foreignKeys = [
        ForeignKey(
            entity = AskChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AskChatSessionEntity(
    @ColumnInfo(name = "chat_id")
    val chatId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "position")
    val position: Int
)

/**
 * One question or answer in a chat.
 *
 * @property role `USER` or `ASSISTANT` ([com.meetmind.assistant.domain.model.AskRole] name).
 * @property citationsJson JSON array of citation snapshots; null when there are none.
 */
@Entity(
    tableName = "ask_messages",
    foreignKeys = [
        ForeignKey(
            entity = AskChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chat_id"])
    ]
)
data class AskMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "chat_id")
    val chatId: String,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "citations_json")
    val citationsJson: String? = null,

    @ColumnInfo(name = "not_found")
    val notFound: Boolean = false
)

/** Row of the Saved chats query; see [com.meetmind.assistant.data.database.dao.AskChatDao]. */
data class AskChatSummaryRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "pinned") val pinned: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "missing_session_count") val missingSessionCount: Int,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    @ColumnInfo(name = "first_question") val firstQuestion: String?,
    @ColumnInfo(name = "last_answer") val lastAnswer: String?,
    @ColumnInfo(name = "first_session_name") val firstSessionName: String?
)
