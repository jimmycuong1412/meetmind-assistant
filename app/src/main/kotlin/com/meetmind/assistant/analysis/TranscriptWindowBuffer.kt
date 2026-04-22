// T005 (spec 008): Rolling transcript window buffer for continuous analysis
package com.meetmind.assistant.analysis

/**
 * A single ASR segment with its capture timestamp.
 *
 * @param timestampMs Monotonically increasing timestamp (System.currentTimeMillis() or injected clock).
 * @param text        Transcribed text for this segment.
 */
data class TimestampedSegment(val timestampMs: Long, val text: String)

/**
 * Rolling buffer of [TimestampedSegment]s trimmed to a configurable time window.
 *
 * Thread-safety: NOT thread-safe. Access must be confined to a single coroutine/thread
 * (the audio processing coroutine owns the buffer; analysis reads via [windowText] on the
 * same dispatcher).
 *
 * Data minimisation (Principle I / FR-003): [windowText] trims from the tail to ≤ 600 chars
 * before returning so callers never receive more than the truncated window.
 */
class TranscriptWindowBuffer {

    private val segments: ArrayDeque<TimestampedSegment> = ArrayDeque()

    /**
     * Appends [segment] to the buffer and eagerly evicts segments older than [windowSizeMs].
     *
     * @param segment     The new ASR segment to add.
     * @param windowSizeMs Rolling window in milliseconds; segments with
     *                     `timestampMs < segment.timestampMs - windowSizeMs` are evicted.
     */
    fun append(segment: TimestampedSegment, windowSizeMs: Long) {
        segments.addLast(segment)
        val cutoff = segment.timestampMs - windowSizeMs
        while (segments.isNotEmpty() && segments.first().timestampMs < cutoff) {
            segments.removeFirst()
        }
    }

    /**
     * Returns the concatenated text of all segments within the last [windowSizeMs],
     * truncated to ≤ 600 characters from the tail (most recent text).
     *
     * @param windowSizeMs Age threshold in milliseconds.
     * @return Window text, or empty string if buffer is empty.
     */
    fun windowText(windowSizeMs: Long): String {
        if (segments.isEmpty()) return ""
        val nowMs = segments.last().timestampMs
        val cutoff = nowMs - windowSizeMs
        val full = segments
            .filter { it.timestampMs >= cutoff }
            .joinToString(separator = " ") { it.text }
        // Truncate to 600 chars from the tail (keep the most recent content)
        return if (full.length <= MAX_CHARS) full else full.takeLast(MAX_CHARS)
    }

    /** Returns the current number of segments in the buffer. */
    fun size(): Int = segments.size

    /** Clears all segments (call on session end). */
    fun clear() = segments.clear()

    companion object {
        /** Maximum chars returned by [windowText] — enforces FR-004 data minimisation. */
        const val MAX_CHARS = 600
    }
}
