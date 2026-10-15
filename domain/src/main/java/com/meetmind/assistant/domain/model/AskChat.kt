package com.meetmind.assistant.domain.model

/**
 * A saved "Ask MeetMind" conversation: questions asked about a fixed set of meetings.
 *
 * The chat stores the ids of the sessions it reads rather than owning them, so a
 * session deleted later simply drops out of [sessionIds] resolution and is reported
 * as missing (see [AskChatSummary.missingSessionCount]).
 *
 * @property title User-chosen name, or null to fall back to the first question.
 * @property pinned Pinned chats sort first in Saved chats.
 */
data class AskChat(
    val id: String,
    val title: String?,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val sessionIds: List<String>,
    val messages: List<AskMessage>
)

/**
 * One row of the Saved chats list. Built by a single query, so it carries the
 * counts and previews the list shows instead of the full message history.
 *
 * @property firstQuestion Fallback title when [title] is null.
 * @property lastAnswer Preview line; null until the first answer arrives.
 * @property singleSessionName Name of the only meeting, when the chat reads exactly one.
 */
data class AskChatSummary(
    val id: String,
    val title: String?,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val sessionCount: Int,
    val missingSessionCount: Int,
    val questionCount: Int,
    val firstQuestion: String?,
    val lastAnswer: String?,
    val singleSessionName: String?
) {
    /** Name to show: the user's title, else the first question, else null (caller localizes). */
    val displayTitle: String?
        get() = title?.takeIf { it.isNotBlank() } ?: firstQuestion
}

enum class AskRole { USER, ASSISTANT }

/**
 * A single message in an [AskChat].
 *
 * @property text For assistant messages, the answer with citation markers rewritten
 *   to `[1]`, `[2]`… matching [citations] order.
 * @property notFound True when the model reported the answer is not in the meetings.
 */
data class AskMessage(
    val id: String,
    val chatId: String,
    val role: AskRole,
    val text: String,
    val createdAt: Long,
    val citations: List<AskCitation> = emptyList(),
    val notFound: Boolean = false
)

/**
 * A source an answer points at. The quote is snapshotted when the answer is saved so
 * the citation still reads correctly if the transcript is edited or the meeting deleted.
 *
 * @property number 1-based marker used in the answer text (`[number]`).
 * @property segmentId First transcript segment of the cited passage; null for insight passages.
 * @property offsetMs Position of the passage in the recording, for the "14:32" label.
 */
data class AskCitation(
    val number: Int,
    val sessionId: String,
    val sessionName: String?,
    val segmentId: String?,
    val offsetMs: Long?,
    val speaker: String?,
    val quote: String
)
