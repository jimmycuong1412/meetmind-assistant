// T010–T017, T023 (spec 007): Contract tests for OnDeviceLlamaProvider
package com.meetmind.assistant.inference

import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.ModelLoadState
import com.meetmind.assistant.helpers.FakeLlamaJni
import com.meetmind.assistant.helpers.testDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contract tests for [OnDeviceLlamaProvider].
 *
 * Contracts tested:
 *   C1.1 — emits ≥ 1 Token before Complete
 *   C1.2 — Complete.fullText == concatenation of all Token.text values
 *   C1.3 — model not loaded → emits Error, no exception to collector
 *   C1.4 — blank questionText → emits Error("empty question")
 *   C3.1 — FakeLlamaJni returns handle > 0 for valid path
 *   C3.2 — FakeLlamaJni returns -1 for /nonexistent/ paths
 *   C3.3 — unloadModel is idempotent (no crash on double-call)
 *   C5.1 — questionText > 600 chars is truncated to 600 before JNI call
 *   C5.3 — buildSystemPrompt() returns ≤ 320 chars
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnDeviceLlamaProviderTest {

    /** Each test gets its own TestScope so DataStore coroutines complete within runTest. */
    private fun makeRepo(scope: TestScope = TestScope(UnconfinedTestDispatcher())) =
        ModelConfigRepository(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            dataStore = testDataStore(scope)
        )

    private fun makeProvider(
        fake: FakeLlamaJni = FakeLlamaJni(),
        repo: ModelConfigRepository = makeRepo()
    ): OnDeviceLlamaProvider = OnDeviceLlamaProvider(
        configRepository = repo,
        backend = fake,
        loadDispatcher = UnconfinedTestDispatcher()
    )

    // ── C1.1: emits ≥ 1 Token before Complete ─────────────────────────────────

    @Test
    fun `C1-1 generate emits at least one Token before Complete`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni(tokensToEmit = listOf("Meeting", " tip:", " stay", " focused."))
        val provider = makeProvider(fake)
        provider.loadModel()

        val events = provider.generate("What is agile?").toList()

        val tokens = events.filterIsInstance<InferenceEvent.Token>()
        val completes = events.filterIsInstance<InferenceEvent.Complete>()

        assertTrue("Expected ≥ 1 Token event", tokens.isNotEmpty())
        assertEquals("Expected exactly 1 Complete event", 1, completes.size)
        assertTrue("Complete must be the last event", events.last() is InferenceEvent.Complete)
    }

    // ── C1.2: Complete.fullText == joined tokens ───────────────────────────────

    @Test
    fun `C1-2 Complete fullText equals concatenation of all Token texts`() = runTest(UnconfinedTestDispatcher()) {
        val tokenList = listOf("Focus", " on", " outcomes.")
        val fake = FakeLlamaJni(tokensToEmit = tokenList)
        val provider = makeProvider(fake)
        provider.loadModel()

        val events = provider.generate("How should I run a standup?").toList()

        val emittedTokens = events.filterIsInstance<InferenceEvent.Token>().map { it.text }
        val complete = events.filterIsInstance<InferenceEvent.Complete>().first()

        assertEquals(tokenList.joinToString(""), emittedTokens.joinToString(""))
        assertEquals(tokenList.joinToString(""), complete.fullText)
    }

    // ── C1.3: model not loaded → Error event, no exception ────────────────────

    @Test
    fun `C1-3 generate emits Error when model is not loaded`() = runTest(UnconfinedTestDispatcher()) {
        val provider = makeProvider() // no loadModel() call

        val events = provider.generate("Test question").toList()

        assertEquals(1, events.size)
        assertTrue("Expected Error event", events[0] is InferenceEvent.Error)
    }

    // ── C1.4: blank questionText → Error("empty question") ────────────────────

    @Test
    fun `C1-4 generate emits Error for blank questionText`() = runTest(UnconfinedTestDispatcher()) {
        val provider = makeProvider()
        provider.loadModel()

        val events = provider.generate("   ").toList()

        assertEquals(1, events.size)
        val error = events[0] as InferenceEvent.Error
        assertEquals("empty question", error.message)
    }

    @Test
    fun `C1-4b generate emits Error for empty string`() = runTest(UnconfinedTestDispatcher()) {
        val provider = makeProvider()
        provider.loadModel()

        val events = provider.generate("").toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is InferenceEvent.Error)
    }

    // ── C3.1: FakeLlamaJni returns handle > 0 for valid path ──────────────────

    @Test
    fun `C3-1 loadModel returns Ready with handle greater than zero`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni()
        val provider = makeProvider(fake)

        val state = provider.loadModel()

        assertTrue("Expected Ready state", state is ModelLoadState.Ready)
        assertTrue("Handle must be > 0", (state as ModelLoadState.Ready).handle > 0)
    }

    // ── C3.2: FakeLlamaJni returns -1 for /nonexistent/ ───────────────────────

    @Test
    fun `C3-2 loadModel returns Error when backend returns -1`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo()
        repo.setModelPath("/nonexistent/model.gguf")

        val fake = FakeLlamaJni()  // FakeLlamaJni returns -1 for paths starting with /nonexistent
        val provider = makeProvider(fake, repo)

        val state = provider.loadModel()

        assertTrue("Expected Error state for nonexistent path", state is ModelLoadState.Error)
    }

    // ── C3.3: unloadModel is idempotent ───────────────────────────────────────

    @Test
    fun `C3-3 unload is idempotent - calling twice does not crash`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni()
        val provider = makeProvider(fake)
        provider.loadModel()

        provider.unload()
        provider.unload() // second call must not throw — provider guards with modelHandle > 0

        // Only 1 actual backend call: second unload() is a no-op (modelHandle already -1)
        assertEquals("unloadModel should have been called exactly once", 1, fake.unloadCallCount)
    }

    @Test
    fun `C3-3b unload before load does not crash`() {
        val provider = makeProvider()
        provider.unload() // called before loadModel() — should be a no-op
    }

    // ── C5.1: questionText > 600 chars is truncated ────────────────────────────

    @Test
    fun `C5-1 questionText longer than 600 chars is truncated to 600 before JNI call`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni(tokensToEmit = listOf("ok"))
        val provider = makeProvider(fake)
        provider.loadModel()

        val longQuestion = "Q".repeat(800)
        provider.generate(longQuestion).toList()

        assertTrue(
            "JNI userText must be ≤ 600 chars (was ${fake.lastUserText.length})",
            fake.lastUserText.length <= 600
        )
        assertEquals(600, fake.lastUserText.length)
    }

    @Test
    fun `C5-1b questionText at exactly 600 chars is not truncated`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni(tokensToEmit = listOf("ok"))
        val provider = makeProvider(fake)
        provider.loadModel()

        val exactQuestion = "Q".repeat(600)
        provider.generate(exactQuestion).toList()

        assertEquals(600, fake.lastUserText.length)
    }

    // ── C5.3: system prompt ≤ 320 chars ───────────────────────────────────────

    @Test
    fun `C5-3 buildSystemPrompt returns at most 320 characters`() {
        val prompt = OnDeviceLlamaProvider.buildSystemPrompt()
        assertTrue(
            "System prompt must be ≤ 320 chars (was ${prompt.length})",
            prompt.length <= 320
        )
    }

    @Test
    fun `C5-3b system prompt passed to JNI is at most 320 chars`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni(tokensToEmit = listOf("ok"))
        val provider = makeProvider(fake)
        provider.loadModel()

        provider.generate("Any question").toList()

        assertTrue(
            "JNI systemPrompt must be ≤ 320 chars (was ${fake.lastSystemPrompt.length})",
            fake.lastSystemPrompt.length <= 320
        )
    }

    // ── Additional: inference error from native layer propagates ──────────────

    @Test
    fun `native inference error closes flow with exception`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni(inferShouldError = true)
        val provider = makeProvider(fake)
        provider.loadModel()

        var caught: Throwable? = null
        try {
            provider.generate("question").toList()
        } catch (e: Exception) {
            caught = e
        }

        assertTrue("Expected exception from native error", caught != null)
        assertTrue(caught!!.message?.contains("inference error") == true)
    }
}
