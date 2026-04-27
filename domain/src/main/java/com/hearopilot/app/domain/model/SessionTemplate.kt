package com.hearopilot.app.domain.model

/**
 * A saved session configuration preset that the user can reuse.
 *
 * Stored as a JSON list in DataStore; no DB table needed.
 *
 * @property id UUID stable identifier
 * @property name Human-readable preset name (e.g. "Weekly standup")
 * @property mode Recording mode
 * @property inputLanguage BCP-47 language code for speech recognition
 * @property outputLanguage Optional translation target language
 * @property insightStrategy REAL_TIME or END_OF_SESSION
 * @property topic Optional pre-filled topic/context hint
 * @property createdAt Unix timestamp (ms) for ordering
 */
data class SessionTemplate(
    val id: String,
    val name: String,
    val mode: RecordingMode,
    val inputLanguage: String,
    val outputLanguage: String?,
    val insightStrategy: InsightStrategy,
    val topic: String?,
    val createdAt: Long
)
