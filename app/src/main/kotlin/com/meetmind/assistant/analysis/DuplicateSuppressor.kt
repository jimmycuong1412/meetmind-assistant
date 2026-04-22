// T010 (spec 008): Duplicate analysis card suppressor
package com.meetmind.assistant.analysis

/**
 * Suppresses duplicate analysis cards within a 60-second sliding window.
 *
 * Algorithm (research.md Q5):
 * - Compute a 64-char normalised prefix from the suggestion text
 *   (lowercase, alphanumeric + spaces only, leading whitespace stripped).
 * - Store prefix → timestampMs in a [LinkedHashMap].
 * - On [isSuppressed]: if the prefix exists and is within [TTL_MS], return true.
 * - On [record]: add/update the prefix entry; evict entries older than [TTL_MS].
 *
 * Not thread-safe — must be accessed from a single coroutine context.
 *
 * @param clock Optional clock function for testability; defaults to [System.currentTimeMillis].
 */
class DuplicateSuppressor(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val seen: LinkedHashMap<String, Long> = LinkedHashMap()

    /**
     * Returns true if [text] was already shown within the last [TTL_MS] milliseconds.
     */
    fun isSuppressed(text: String): Boolean {
        val prefix = normalise(text)
        val seenAt = seen[prefix] ?: return false
        return (clock() - seenAt) < TTL_MS
    }

    /**
     * Records [text] as shown at the current time and evicts expired entries.
     */
    fun record(text: String) {
        val prefix = normalise(text)
        seen[prefix] = clock()
        evictExpired()
    }

    private fun normalise(text: String): String =
        text.lowercase()
            .filter { it.isLetterOrDigit() || it == ' ' }
            .trimStart()
            .take(PREFIX_LENGTH)

    private fun evictExpired() {
        val now = clock()
        val iter = seen.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value >= TTL_MS) iter.remove() else break
        }
    }

    companion object {
        const val TTL_MS = 60_000L   // 60 seconds
        const val PREFIX_LENGTH = 64 // chars of normalised prefix used as fingerprint
    }
}
