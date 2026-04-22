// T025-T026: TTFT (Time-To-First-Token) measurement tests — Contract 4
package com.meetmind.assistant.inference

import androidx.test.core.app.ApplicationProvider
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.FallbackReason
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.SessionMode
import com.meetmind.assistant.helpers.FakeApiKeyStore
import com.meetmind.assistant.helpers.FakeCloudStreamingProvider
import com.meetmind.assistant.helpers.FakeClock
import com.meetmind.assistant.helpers.enableProvider
import com.meetmind.assistant.helpers.testDataStore
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class TtftTest {

    private val fallbackProvider = CloudInferenceEngine.OnDeviceFallback { _ ->
        flow { emit(InferenceEvent.Token("fallback-id", "[on-device]")) }
    }

    private fun makeRequest() = CloudInferenceRequest(
        requestId = UUID.randomUUID().toString(),
        questionText = "What is TTFT?",
        systemPrompt = "You help with tests.",
        provider = CloudProvider.CLAUDE,
        sessionMode = SessionMode.INTERVIEW
    )

    private fun makeComponents(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher()
    ): Pair<FakeApiKeyStore, CloudProviderConfigRepository> {
        val scope = TestScope(dispatcher)
        val apiKeyStore = FakeApiKeyStore()
        val repo = CloudProviderConfigRepository(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            dataStore = testDataStore(scope)
        )
        return Pair(apiKeyStore, repo)
    }

    @Before
    fun setUp() {
        ShadowLog.setupLogging()
    }

    /** Contract 4.1: TTFT measured within budget (≤ 5000 ms) */
    @Test
    fun ttft_withinBudget() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        configRepo.enableProvider(CloudProvider.CLAUDE, apiKeyStore)

        val clock = FakeClock(start = 0L)
        val fakeProvider = FakeCloudStreamingProvider(tokens = listOf("Hello"))
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = fakeProvider,
            claudeProviderFactory = { _ -> fakeProvider },
            onDeviceFallback = fallbackProvider,
            clock = clock,
            connectivityChecker = { true }
        )

        clock.advanceBy(2_000L)
        val events = engine.streamSuggestion(makeRequest()).toList()

        assertTrue(events.none { it is InferenceEvent.FallbackActivated })
        assertTrue(events.any { it is InferenceEvent.Token })
    }

    /** Contract 4.2: TTFT exceeds budget (6s > 5s timeout) → TIMEOUT fallback */
    @Test
    fun ttft_exceedsBudget_triggersFallback() = runTest {
        val (apiKeyStore, configRepo) = makeComponents(StandardTestDispatcher(testScheduler))
        configRepo.enableProvider(CloudProvider.CLAUDE, apiKeyStore)

        val slowProvider = FakeCloudStreamingProvider(
            tokens = listOf("Never"),
            firstTokenDelayMs = 6_000L
        )
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = slowProvider,
            claudeProviderFactory = { _ -> slowProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = { true }
        )

        val events = mutableListOf<InferenceEvent>()
        val job = launch {
            engine.streamSuggestion(makeRequest()).collect { events.add(it) }
        }
        advanceTimeBy(5_001L)
        job.join()

        val fallback = events.filterIsInstance<InferenceEvent.FallbackActivated>()
        assertEquals(1, fallback.size)
        assertEquals(FallbackReason.TIMEOUT, fallback.first().reason)
        // No cloud Token ("Never") — only on-device fallback token may appear
        val cloudTokens = events.filterIsInstance<InferenceEvent.Token>().filter { it.text == "Never" }
        assertTrue("Cloud token 'Never' must not appear before timeout", cloudTokens.isEmpty())
    }

    /** Contract 4.3: TTFT is logged via Log.d("CloudInferenceEngine", "TTFT[...]=...ms") */
    @Test
    fun ttft_logged_toLogcat() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        configRepo.enableProvider(CloudProvider.CLAUDE, apiKeyStore)

        val fakeProvider = FakeCloudStreamingProvider(tokens = listOf("Logged"))
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = fakeProvider,
            claudeProviderFactory = { _ -> fakeProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = { true }
        )

        engine.streamSuggestion(makeRequest()).toList()

        val logItems = ShadowLog.getLogs()
        val ttftLog = logItems.find { it.tag == "CloudInferenceEngine" && it.msg.contains("TTFT[") }
        assertTrue("Expected TTFT log in CloudInferenceEngine tag, got: $logItems", ttftLog != null)
        assertTrue("TTFT log should contain 'ms', got: ${ttftLog?.msg}", ttftLog?.msg?.contains("ms") == true)
    }
}
