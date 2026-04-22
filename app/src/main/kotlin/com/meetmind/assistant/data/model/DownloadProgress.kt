package com.meetmind.assistant.data.model

/**
 * Progress update emitted by [ModelDownloadManager] as a Flow.
 *
 * spec 009 — T006
 */
data class DownloadProgress(
    val filename: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean
) {
    /** 0.0f → 1.0f progress fraction; 0f when totalBytes is unknown (chunked transfer). */
    val progressFraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}
