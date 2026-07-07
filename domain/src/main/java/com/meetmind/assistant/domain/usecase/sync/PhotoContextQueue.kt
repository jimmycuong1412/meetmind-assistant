package com.meetmind.assistant.domain.usecase.sync

/**
 * Thread-safe FIFO buffer of photo descriptions produced by AnalyzePhotoUseCase and
 * consumed by SyncSttLlmUseCase at the next insight tick.
 *
 * Producer: MainViewModel (after vision analysis completes).
 * Consumer: SyncSttLlmUseCase.buildUserPrompt (drains once per tick).
 *
 * Bounded at [MAX_PENDING]: if the user captures faster than insight ticks consume
 * (e.g. REAL_TIME_TRANSLATION mode never drains), the oldest entries are dropped.
 * Descriptions are also persisted per-photo in the session_photos table, so a
 * dropped queue entry never loses data — it only stops influencing the next insight.
 */
class PhotoContextQueue {

    private val pending = ArrayDeque<String>()
    private val lock = Any()

    fun add(description: String) {
        if (description.isBlank()) return
        synchronized(lock) {
            pending.addLast(description)
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
    }

    fun drain(): List<String> = synchronized(lock) {
        val all = pending.toList()
        pending.clear()
        all
    }

    companion object {
        const val MAX_PENDING = 10

        /** Renders drained descriptions as a numbered block for the user prompt. */
        fun formatBlock(descriptions: List<String>): String {
            if (descriptions.isEmpty()) return ""
            return buildString {
                append("Photos captured during this period:")
                descriptions.forEachIndexed { i, desc ->
                    append("\nPhoto ${i + 1}: $desc")
                }
            }
        }
    }
}
