package com.meetmind.assistant.data

import com.meetmind.assistant.data.model.GemmaModelVariant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for [DeviceTierDetector] RAM + SDK → GemmaModelVariant recommendation.
 *
 * C4.1 — ≥8 GB RAM + SDK ≥ 33 → recommends Q4_K_M
 * C4.2 — < 8 GB RAM or SDK < 33 → recommends IQ4_NL
 *
 * spec 009 — T027
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class DeviceTierDetectorTest {

    private val detector = DeviceTierDetector()

    /**
     * C4.1 — Flagship device (12 GB, Android 14 / SDK 34) → Q4_K_M.
     * Matches Lenovo Y700 Gen 3 and Honor Magic 6 Pro.
     */
    @Test
    fun `C4_1 flagship device recommends Q4_K_M`() {
        val result = detector.recommendedVariant(
            totalRamBytes = 12_000_000_000L,  // Y700 Gen 3 / Honor Magic 6 Pro
            sdkVersion = 34                    // Android 14
        )
        assertEquals(GemmaModelVariant.Q4_K_M, result)
    }

    @Test
    fun `C4_1b exactly 8 GB and SDK 33 qualifies for Q4_K_M`() {
        val result = detector.recommendedVariant(
            totalRamBytes = 8_000_000_000L,
            sdkVersion = 33
        )
        assertEquals(GemmaModelVariant.Q4_K_M, result)
    }

    /**
     * C4.2 — Low-RAM device (4 GB, Android 13) → IQ4_NL.
     */
    @Test
    fun `C4_2 low RAM device recommends IQ4_NL`() {
        val result = detector.recommendedVariant(
            totalRamBytes = 4_000_000_000L,
            sdkVersion = 33
        )
        assertEquals(GemmaModelVariant.IQ4_NL, result)
    }

    /**
     * C4.2 — Sufficient RAM but old SDK → IQ4_NL.
     */
    @Test
    fun `C4_2b old SDK regardless of RAM recommends IQ4_NL`() {
        val result = detector.recommendedVariant(
            totalRamBytes = 12_000_000_000L,
            sdkVersion = 32  // Android 12 — below threshold
        )
        assertEquals(GemmaModelVariant.IQ4_NL, result)
    }

    /**
     * C4.2 — Boundary: just below 8 GB → IQ4_NL.
     */
    @Test
    fun `C4_2c just below 8 GB recommends IQ4_NL`() {
        val result = detector.recommendedVariant(
            totalRamBytes = 7_999_999_999L,
            sdkVersion = 34
        )
        assertEquals(GemmaModelVariant.IQ4_NL, result)
    }
}
