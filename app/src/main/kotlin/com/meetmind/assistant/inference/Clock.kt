// T009: Clock abstraction for deterministic TTFT measurement in tests
package com.meetmind.assistant.inference

/**
 * Abstracts wall-clock time so TTFT measurement in [CloudInferenceEngine] is
 * deterministic in tests.
 *
 * Production uses [SystemClock]; tests inject [FakeClock] (in src/test/).
 */
interface Clock {
    fun nowMs(): Long
}

/** Production clock backed by [System.currentTimeMillis]. */
object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
