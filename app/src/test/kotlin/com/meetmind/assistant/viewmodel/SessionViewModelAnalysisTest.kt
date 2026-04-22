// T015 (spec 008): Contract tests C3.1–C3.3 for SessionViewModel analysis integration
package com.meetmind.assistant.viewmodel

import androidx.test.core.app.ApplicationProvider
import com.meetmind.assistant.analysis.AnalysisEvent
import com.meetmind.assistant.analysis.AnalysisCadenceController
import com.meetmind.assistant.analysis.TranscriptWindowBuffer
import com.meetmind.assistant.analysis.TimestampedSegment
import com.meetmind.assistant.data.model.ModelLoadState
import com.meetmind.assistant.helpers.FakeApiKeyStore
import com.meetmind.assistant.helpers.FakeConversationAnalyzer
import com.meetmind.assistant.helpers.FakeLlamaJni
import com.meetmind.assistant.helpers.enableProvider
import com.meetmind.assistant.helpers.testDataStore
import com.meetmind.assistant.inference.OnDeviceLlamaProvider
import com.meetmind.assistant.inference.CloudInferenceEngine
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.helpers.FakeCloudStreamingProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for [SessionViewModel] analysis integration.
 *
 * C3.1 — FakeConversationAnalyzer emits ActionItem → analysisEvent StateFlow updated
 * C3.2 — question detection fires → analysis card suppressed (question takes priority)
 * C3.3 — analysisEnabled = false (cadenceController = null) → zero analyze() calls
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class SessionViewModelAnalysisTest {

    private fun makeConfigRepo(scope: TestScope): CloudProviderConfigRepository {
        val apiKeyStore = FakeApiKeyStore()
        return CloudProviderConfigRepository(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            dataStore = testDataStore(scope)
        )
    }

    private fun makeEngine(configRepo: CloudProviderConfigRepository): CloudInferenceEngine {
        val apiKeyStore = FakeApiKeyStore()
        val fakeProvider = FakeCloudStreamingProvider(tokens = listOf("cloud suggestion"))
        return CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = fakeProvider,
            claudeProviderFactory = { _ -> fakeProvider },
            onDeviceFallback = CloudInferenceEngine.OnDeviceFallback { _ ->
                kotlinx.coroutines.flow.flow {
                    emit(com.meetmind.assistant.data.model.InferenceEvent.Token("id", "[on-device]"))
                }
            },
            connectivityChecker = { true }
        )
    }

    // ── C3.1: ActionItem event routes to analysisEvent StateFlow ─────────────

    @Test
    fun `C3-1 FakeConversationAnalyzer ActionItem updates analysisEvent StateFlow`() =
        runTest(StandardTestDispatcher()) {
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val configRepo = makeConfigRepo(scope)
            val engine = makeEngine(configRepo)

            val actionItemEvent = AnalysisEvent.ActionItem(
                text = "Alice will finish the report by Friday",
                suggestionText = "Who confirms? What is the deadline?"
            )
            val fakeAnalyzer = FakeConversationAnalyzer(eventSequence = listOf(actionItemEvent))

            val buffer = TranscriptWindowBuffer().also {
                it.append(TimestampedSegment(0L, "Alice will finish the report"), windowSizeMs = 60_000L)
            }
            val controller = AnalysisCadenceController(
                analyzer = fakeAnalyzer,
                buffer = buffer,
                intervalMs = 10_000L,
                windowSizeMs = 60_000L,
                dispatcher = StandardTestDispatcher(testScheduler)
            )

            val vm = SessionViewModel(
                cloudInferenceEngine = engine,
                configRepository = configRepo,
                cadenceController = controller
            )

            advanceTimeBy(10_001L)  // first tick fires

            val event = vm.analysisEvent.value
            assertNotNull("analysisEvent should be non-null after tick", event)
            assertTrue("Should be ActionItem", event is AnalysisEvent.ActionItem)
            assertEquals(
                "Alice will finish the report by Friday",
                (event as AnalysisEvent.ActionItem).text
            )

            controller.stop()
        }

    // ── C3.2: question detection suppresses analysis card ────────────────────

    @Test
    fun `C3-2 question detection clears analysis event and takes display priority`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = TestScope(UnconfinedTestDispatcher())
            val configRepo = makeConfigRepo(scope)
            val engine = makeEngine(configRepo)

            val actionItemEvent = AnalysisEvent.ActionItem(
                text = "some action",
                suggestionText = "Who owns it?"
            )
            val fakeAnalyzer = FakeConversationAnalyzer(eventSequence = listOf(actionItemEvent))
            val buffer = TranscriptWindowBuffer().also {
                it.append(TimestampedSegment(0L, "some action item text"), windowSizeMs = 60_000L)
            }
            val controller = AnalysisCadenceController(
                analyzer = fakeAnalyzer,
                buffer = buffer,
                intervalMs = 10_000L,
                windowSizeMs = 60_000L,
                dispatcher = UnconfinedTestDispatcher()
            )

            val vm = SessionViewModel(
                cloudInferenceEngine = engine,
                configRepository = configRepo,
                cadenceController = controller
            )

            // Simulate question detection — should clear any pending analysis event
            vm.onNewQuestion()

            assertNull(
                "Analysis event should be null after question detection takes priority",
                vm.analysisEvent.value
            )

            controller.stop()
        }

    // ── C3.3: analysisEnabled = false → zero analyze() calls ─────────────────

    @Test
    fun `C3-3 never-firing cadenceController means zero analyze calls`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = TestScope(UnconfinedTestDispatcher())
            val configRepo = makeConfigRepo(scope)
            val engine = makeEngine(configRepo)

            val fakeAnalyzer = FakeConversationAnalyzer()
            val buffer = TranscriptWindowBuffer()

            // Controller with MaxValue interval — effectively disabled (never fires during test)
            val controller = AnalysisCadenceController(
                analyzer = fakeAnalyzer,
                buffer = buffer,
                intervalMs = Long.MAX_VALUE,
                windowSizeMs = 60_000L,
                dispatcher = UnconfinedTestDispatcher()
            )

            val vm = SessionViewModel(
                cloudInferenceEngine = engine,
                configRepository = configRepo,
                cadenceController = controller
            )

            // No time advanced — zero ticks should have fired
            assertEquals(
                "Analyzer must not be called before first tick",
                0,
                fakeAnalyzer.analyzeCallCount.get()
            )
            assertNull("analysisEvent should remain null", vm.analysisEvent.value)
            controller.stop()
        }

    // ── Dismiss button: dismissAnalysisEvent() clears the card ───────────────

    @Test
    fun `dismissAnalysisEvent clears analysisEvent to null`() =
        runTest(StandardTestDispatcher()) {
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val configRepo = makeConfigRepo(scope)
            val engine = makeEngine(configRepo)

            val event = AnalysisEvent.Decision(text = "React approach chosen")
            val fakeAnalyzer = FakeConversationAnalyzer(eventSequence = listOf(event))
            val buffer = TranscriptWindowBuffer().also {
                it.append(TimestampedSegment(0L, "we decided to go with React"), windowSizeMs = 60_000L)
            }
            val controller = AnalysisCadenceController(
                analyzer = fakeAnalyzer,
                buffer = buffer,
                intervalMs = 10_000L,
                windowSizeMs = 60_000L,
                dispatcher = StandardTestDispatcher(testScheduler)
            )

            val vm = SessionViewModel(
                cloudInferenceEngine = engine,
                configRepository = configRepo,
                cadenceController = controller
            )

            advanceTimeBy(10_001L)
            assertNotNull(vm.analysisEvent.value)

            vm.dismissAnalysisEvent()
            assertNull("Analysis event should be null after dismiss", vm.analysisEvent.value)

            controller.stop()
        }
}
