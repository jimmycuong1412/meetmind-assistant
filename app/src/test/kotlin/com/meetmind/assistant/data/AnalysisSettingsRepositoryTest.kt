// T023 (spec 008): Contract tests for AnalysisSettingsRepository
package com.meetmind.assistant.data

import com.meetmind.assistant.data.model.AnalysisSettings
import com.meetmind.assistant.helpers.testDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for [AnalysisSettingsRepository].
 *
 * Covers: enabled/disabled read-write, interval clamping 10..120, window clamping 15..300,
 * DataStore key prefix `analysis_`, and default values.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class AnalysisSettingsRepositoryTest {

    private fun makeRepo(scope: TestScope = TestScope(UnconfinedTestDispatcher())) =
        AnalysisSettingsRepository(dataStore = testDataStore(scope))

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `defaults are correct on first read`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        val settings = repo.observe().first()
        assertEquals(AnalysisSettings.DEFAULT_ENABLED, settings.analysisEnabled)
        assertEquals(AnalysisSettings.DEFAULT_INTERVAL_S, settings.analysisIntervalS)
        assertEquals(AnalysisSettings.DEFAULT_WINDOW_S, settings.analysisWindowS)
    }

    // ── enabled toggle ────────────────────────────────────────────────────────

    @Test
    fun `setEnabled persists false`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setEnabled(false)
        assertFalse(repo.observe().first().analysisEnabled)
    }

    @Test
    fun `setEnabled persists true`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setEnabled(false)
        repo.setEnabled(true)
        assertTrue(repo.observe().first().analysisEnabled)
    }

    // ── interval clamping ─────────────────────────────────────────────────────

    @Test
    fun `setIntervalS clamps below minimum to 10`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setIntervalS(3)
        assertEquals(AnalysisSettings.MIN_INTERVAL_S, repo.observe().first().analysisIntervalS)
    }

    @Test
    fun `setIntervalS clamps above maximum to 120`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setIntervalS(200)
        assertEquals(AnalysisSettings.MAX_INTERVAL_S, repo.observe().first().analysisIntervalS)
    }

    @Test
    fun `setIntervalS persists valid value`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setIntervalS(30)
        assertEquals(30, repo.observe().first().analysisIntervalS)
    }

    @Test
    fun `setIntervalS accepts boundary value 10`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setIntervalS(10)
        assertEquals(10, repo.observe().first().analysisIntervalS)
    }

    @Test
    fun `setIntervalS accepts boundary value 120`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setIntervalS(120)
        assertEquals(120, repo.observe().first().analysisIntervalS)
    }

    // ── window clamping ───────────────────────────────────────────────────────

    @Test
    fun `setWindowS clamps below minimum to 15`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setWindowS(5)
        assertEquals(AnalysisSettings.MIN_WINDOW_S, repo.observe().first().analysisWindowS)
    }

    @Test
    fun `setWindowS clamps above maximum to 300`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setWindowS(500)
        assertEquals(AnalysisSettings.MAX_WINDOW_S, repo.observe().first().analysisWindowS)
    }

    @Test
    fun `setWindowS persists valid value`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo(TestScope(UnconfinedTestDispatcher()))
        repo.setWindowS(90)
        assertEquals(90, repo.observe().first().analysisWindowS)
    }
}
