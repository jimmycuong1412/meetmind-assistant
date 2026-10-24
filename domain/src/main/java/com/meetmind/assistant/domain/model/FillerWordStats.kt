package com.meetmind.assistant.domain.model

/**
 * Running filler-word count for an Interview Mode recording. Live only, never persisted.
 *
 * @property ratePerMinute Fillers per minute of recording. The first minute is divided by
 *   one minute rather than the true elapsed time, so the first few words cannot spike it.
 */
data class FillerWordStats(
    val totalCount: Int = 0,
    val ratePerMinute: Float = 0f
) {
    enum class Level { CALM, ELEVATED, HIGH }

    val level: Level
        get() = when {
            ratePerMinute >= HIGH_RATE -> Level.HIGH
            ratePerMinute >= ELEVATED_RATE -> Level.ELEVATED
            else -> Level.CALM
        }

    companion object {
        const val ELEVATED_RATE = 3f
        const val HIGH_RATE = 6f

        fun of(totalCount: Int, recordingDurationMs: Long): FillerWordStats {
            val minutes = recordingDurationMs / 60_000f
            return FillerWordStats(totalCount, totalCount / maxOf(1f, minutes))
        }
    }
}
