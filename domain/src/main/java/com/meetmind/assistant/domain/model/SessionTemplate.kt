package com.meetmind.assistant.domain.model

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
 * @property candidateProfile Interview Mode only: the candidate's own background —
 *   clouds, scale, tooling, war stories — injected into the prompt so answers cite real
 *   experience instead of textbook generalities.
 *
 *   This is the single biggest lever on answer quality with a small on-device model. A
 *   1B model asked to answer as a "Senior DevOps Engineer" produces advice a senior
 *   interviewer identifies as non-experience-backed within one follow-up; the same model
 *   given "EKS, 40 nodes, ~200 services, Terraform monorepo via Atlantis, war story:
 *   state corruption during a multi-region migration" reaches for the candidate's own
 *   incident instead.
 *
 *   Stored on the template so it is typed once and reused across sessions. Null for
 *   non-interview templates and for templates saved before this field existed.
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
    val candidateProfile: String? = null,
    val createdAt: Long
)
