package com.meetmind.assistant.domain.model

/**
 * A photo captured during a recording session.
 *
 * @property description Vision-model-generated description; null until analysis completes
 *   (or permanently null if analysis failed — the photo itself is still kept).
 */
data class SessionPhoto(
    val id: String,
    val sessionId: String,
    val filePath: String,
    val description: String?,
    val timestamp: Long
)
