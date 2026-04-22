// T018 (spec 008): Contract tests C1.1–C1.6 for DefaultConversationAnalyzer
package com.meetmind.assistant.analysis

import com.meetmind.assistant.helpers.FakeInferenceEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [DefaultConversationAnalyzer].
 *
 * C1.1 — Blank window text → NoSignal, no inference call
 * C1.2 — Question pattern → EventType.QUESTION, delegates to inference engine
 * C1.3 — Action item keywords → EventType.ACTION_ITEM, suggestionText non-null when engine loaded
 * C1.4 — Decision keywords → EventType.DECISION
 * C1.5 — Confusion keywords → EventType.CONFUSION
 * C1.6 — windowText > 600 chars → truncated to 600 before inference call
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationAnalyzerTest {

    private fun makeAnalyzer(
        fake: FakeInferenceEngine = FakeInferenceEngine(),
        modelLoaded: Boolean = true
    ): DefaultConversationAnalyzer = DefaultConversationAnalyzer(
        inferenceEngine = fake,
        duplicateSuppressor = DuplicateSuppressor(),
        isModelLoaded = { modelLoaded }
    )

    // ── C1.1: blank → NoSignal, zero inference calls ──────────────────────────

    @Test
    fun `C1-1 blank window emits NoSignal and makes no inference call`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine()
        val analyzer = makeAnalyzer(fake)

        val events = analyzer.analyze("   ").toList()

        assertEquals(1, events.size)
        assertTrue("Expected NoSignal for blank input", events[0] is AnalysisEvent.NoSignal)
        assertEquals("No inference call should be made", 0, fake.callCount.get())
    }

    @Test
    fun `C1-1b empty string emits NoSignal`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine()
        val analyzer = makeAnalyzer(fake)

        val events = analyzer.analyze("").toList()

        assertTrue(events[0] is AnalysisEvent.NoSignal)
        assertEquals(0, fake.callCount.get())
    }

    // ── C1.2: question pattern → delegates to inference engine ───────────────

    @Test
    fun `C1-2 question pattern emits Question event with suggestion`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine(stubTokens = listOf("Try breaking it down into smaller tasks."))
        val analyzer = makeAnalyzer(fake)

        val event = analyzer.analyze("What is the best approach for this sprint?").first()

        assertTrue("Expected Question event", event is AnalysisEvent.Question)
        assertEquals(1, fake.callCount.get())
        assertNotNull((event as AnalysisEvent.Question).suggestionText)
    }

    // ── C1.3: action item → ActionItem event ─────────────────────────────────

    @Test
    fun `C1-3 action item keywords emit ActionItem with non-null suggestion`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine(stubTokens = listOf("Who confirms? What is the exact deadline?"))
        val analyzer = makeAnalyzer(fake)

        val event = analyzer.analyze("Alice will finish the architecture review by Friday").first()

        assertTrue("Expected ActionItem event", event is AnalysisEvent.ActionItem)
        assertNotNull((event as AnalysisEvent.ActionItem).suggestionText)
        assertEquals(1, fake.callCount.get())
    }

    // ── C1.4: decision → Decision event ──────────────────────────────────────

    @Test
    fun `C1-4 decision keywords emit Decision event`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine(stubTokens = listOf("Document this decision in your notes."))
        val analyzer = makeAnalyzer(fake)

        val event = analyzer.analyze("We decided to go with the React approach for the frontend.").first()

        assertTrue("Expected Decision event", event is AnalysisEvent.Decision)
        assertNotNull((event as AnalysisEvent.Decision).suggestionText)
    }

    // ── C1.5: confusion → Confusion event ────────────────────────────────────

    @Test
    fun `C1-5 confusion signals emit Confusion event`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine(stubTokens = listOf("Could you clarify what you mean by that?"))
        val analyzer = makeAnalyzer(fake)

        val event = analyzer.analyze("I'm not sure I follow what you mean by technical debt here.").first()

        assertTrue("Expected Confusion event", event is AnalysisEvent.Confusion)
        assertNotNull((event as AnalysisEvent.Confusion).suggestionText)
    }

    // ── C1.6: 600-char truncation ─────────────────────────────────────────────

    @Test
    fun `C1-6 windowText over 600 chars is truncated to 600 before inference call`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine(stubTokens = listOf("ok"))
        val analyzer = makeAnalyzer(fake)

        val longText = "What is the plan? " + "x".repeat(700)  // contains question → triggers inference
        analyzer.analyze(longText).toList()

        assertTrue(
            "Inference must receive ≤ 600 chars (was ${fake.lastReceivedText.length})",
            fake.lastReceivedText.length <= 600
        )
        assertEquals(600, fake.lastReceivedText.length)
    }

    // ── No model loaded → label-only card (suggestionText = null) ─────────────

    @Test
    fun `no model loaded emits ActionItem with null suggestionText`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine()
        val analyzer = makeAnalyzer(fake, modelLoaded = false)

        val event = analyzer.analyze("Alice will finish the report by Friday").first()

        assertTrue("Expected ActionItem even without model", event is AnalysisEvent.ActionItem)
        assertNull("suggestionText should be null when model not loaded", (event as AnalysisEvent.ActionItem).suggestionText)
        assertEquals("No inference call when model not loaded", 0, fake.callCount.get())
    }

    // ── Duplicate suppression: second identical result within 60s is NoSignal ─

    @Test
    fun `duplicate result within 60s is suppressed to NoSignal`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeInferenceEngine(stubTokens = listOf("Document this decision in your notes."))
        val suppressor = DuplicateSuppressor(clock = { 0L }) // frozen clock
        val analyzer = DefaultConversationAnalyzer(
            inferenceEngine = fake,
            duplicateSuppressor = suppressor,
            isModelLoaded = { true }
        )

        val text = "We decided to go with the React approach for the frontend."
        analyzer.analyze(text).toList()   // first — records fingerprint
        val second = analyzer.analyze(text).first()  // second — suppressed

        assertTrue("Second identical result within 60s should be NoSignal", second is AnalysisEvent.NoSignal)
    }
}
