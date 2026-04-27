package com.hearopilot.app.domain.model

/**
 * Represents a single action item extracted from an LLM insight.
 *
 * Action items are parsed from the [LlmInsight.tasks] JSON array when an insight is saved,
 * and stored independently so the user can track their completion status.
 *
 * @property id Unique identifier for this action item
 * @property sessionId ID of the session this item belongs to (for cascade display)
 * @property insightId ID of the insight this item was extracted from
 * @property text The action item description
 * @property isDone Whether the user has marked this item as completed
 * @property createdAt Unix timestamp (ms) when this item was created (mirrors insight timestamp)
 */
data class ActionItem(
    val id: String,
    val sessionId: String,
    val insightId: String,
    val text: String,
    val isDone: Boolean = false,
    val createdAt: Long
)
