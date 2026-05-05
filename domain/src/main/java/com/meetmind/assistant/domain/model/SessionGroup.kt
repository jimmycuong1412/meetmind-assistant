package com.meetmind.assistant.domain.model

/**
 * A named group that users create to organise their transcription sessions.
 *
 * Groups are optional — sessions without a [groupId] are shown under "Other"
 * in the Sessions screen.
 *
 * @property id  UUID identifying this group
 * @property name  User-supplied label (e.g. "Work", "Daily English")
 * @property createdAt  Unix timestamp (ms) when the group was created
 */
data class SessionGroup(
    val id: String,
    val name: String,
    val createdAt: Long
)
