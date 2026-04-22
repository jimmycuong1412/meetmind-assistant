package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.GemmaModelVariant

/**
 * Test double for DeviceTierDetector.
 *
 * Constructor-injected [recommendedVariant] lets tests control the recommendation
 * without needing a real Android Context (for contract C4.1 / C4.2 tests).
 *
 * spec 009 — T011
 */
class FakeDeviceTierDetector(
    val recommendedVariant: GemmaModelVariant = GemmaModelVariant.Q4_K_M
) {
    /** Mirrors the real DeviceTierDetector API. Ignores parameters; returns fixed variant. */
    fun recommendedVariant(totalRamBytes: Long, sdkVersion: Int): GemmaModelVariant =
        recommendedVariant
}
