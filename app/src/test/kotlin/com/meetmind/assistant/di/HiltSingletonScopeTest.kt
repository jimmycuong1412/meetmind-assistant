package com.meetmind.assistant.di

import com.meetmind.assistant.analysis.AnalysisCadenceController
import com.meetmind.assistant.analysis.TranscriptWindowBuffer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * C6.1 — @Singleton scope returns the same instance from two injection points.
 *
 * Both [TranscriptWindowBuffer] and [AnalysisCadenceController] are bound as
 * @Singleton in [AnalysisModule]. Two injection points must resolve to the
 * same object (identity equality).
 *
 * spec 009 — T012
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = dagger.hilt.android.testing.HiltTestApplication::class)
class HiltSingletonScopeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var buffer1: TranscriptWindowBuffer

    @Inject
    lateinit var buffer2: TranscriptWindowBuffer

    @Inject
    lateinit var cadence1: AnalysisCadenceController

    @Inject
    lateinit var cadence2: AnalysisCadenceController

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun `C6_1a TranscriptWindowBuffer singleton returns same instance`() {
        assertSame(
            "TranscriptWindowBuffer must be @Singleton — both injection points must resolve to the same instance",
            buffer1,
            buffer2
        )
    }

    @Test
    fun `C6_1b AnalysisCadenceController singleton returns same instance`() {
        assertSame(
            "AnalysisCadenceController must be @Singleton — both injection points must resolve to the same instance",
            cadence1,
            cadence2
        )
    }
}
