// T020: Deterministic clock for TTFT measurement tests
package com.meetmind.assistant.helpers

import com.meetmind.assistant.inference.Clock

/**
 * Test-only [Clock] whose time advances only when [advanceBy] is explicitly called.
 *
 * Usage:
 * ```
 * val clock = FakeClock()
 * // … do something that records clock.nowMs() …
 * clock.advanceBy(2_000)
 * // … assert TTFT == 2000 …
 * ```
 */
class FakeClock(start: Long = 0L) : Clock {
    private var _now: Long = start

    override fun nowMs(): Long = _now

    fun advanceBy(ms: Long) {
        _now += ms
    }

    fun reset(to: Long = 0L) {
        _now = to
    }
}
