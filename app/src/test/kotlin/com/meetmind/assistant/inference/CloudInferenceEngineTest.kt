// T022-T023: CloudInferenceEngine unit tests — Contract 3 (all 8 test cases)
package com.meetmind.assistant.inference

import androidx.test.core.app.ApplicationProvider
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.FallbackReason
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.SessionMode
import com.meetmind.assistant.helpers.FakeApiKeyStore
import com.meetmind.assistant.helpers.FakeCloudStreamingProvider
import com.meetmind.assistant.helpers.FakeConnectivityChecker
import com.meetmind.assistant.helpers.testDataStore
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CloudInferenceEngineTest {

    private val fallbackProvider = CloudInferenceEngine.OnDeviceFallback { _ ->
        flow { emit(InferenceEvent.Token("fallback-id", "[on-device]")) }
    }

    private fun makeRequest(
        provider: CloudProvider = CloudProvider.CLAUDE,
        questionText: String = "What is Kotlin?",
        systemPrompt: String = "You are a helpful assistant."
    ) = CloudInferenceRequest(
        requestId = UUID.randomUUID().toString(),
        questionText = questionText,
        systemPrompt = systemPrompt,
        provider = provider,
        sessionMode = SessionMode.INTERVIEW
    )

    /** Returns a fresh (apiKeyStore, configRepo) pair backed by an isolated TestScope DataStore. */
    private fun makeComponents(dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher()):
            Pair<FakeApiKeyStore, CloudProviderConfigRepository> {
        val scope = TestScope(dispatcher)
        val apiKeyStore = FakeApiKeyStore()
        val repo = CloudProviderConfigRepository(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            dataStore = testDataStore(scope)
        )
        return Pair(apiKeyStore, repo)
    }

    // ---------- Contract 3 test cases ----------

    /** Contract 3.1: Cloud provider emits tokens then Complete */
    @Test
    fun streamCloud_emitsTokensThenComplete() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val fakeProvider = FakeCloudStreamingProvider(tokens = listOf("Hi", " there"))
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = fakeProvider,
            claudeProviderFactory = { _ -> fakeProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = { true }
        )

        val events = engine.streamSuggestion(makeRequest()).toList()

        val tokens = events.filterIsInstance<InferenceEvent.Token>()
        val complete = events.filterIsInstance<InferenceEvent.Complete>()
        assertEquals(listOf("Hi", " there"), tokens.map { it.text })
        assertEquals(1, complete.size)
        assertEquals("Hi there", complete.first().fullText)
    }

    /** Contract 3.2: Network unavailable triggers fallback with NETWORK_UNAVAILABLE */
    @Test
    fun fallback_networkUnavailable() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val connectivity = FakeConnectivityChecker(isAvailable = false)
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = FakeCloudStreamingProvider(),
            claudeProviderFactory = { _ -> FakeCloudStreamingProvider() },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = connectivity.asLambda
        )

        val events = engine.streamSuggestion(makeRequest()).toList()

        val fallback = events.filterIsInstance<InferenceEvent.FallbackActivated>()
        assertEquals(1, fallback.size)
        assertEquals(FallbackReason.NETWORK_UNAVAILABLE, fallback.first().reason)
    }

    /**
     * Contract 3.3: Provider timeout after 5s triggers TIMEOUT fallback.
     * Uses StandardTestDispatcher so advanceTimeBy controls virtual time.
     */
    @Test
    fun fallback_timeout_5s() = runTest(StandardTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents(StandardTestDispatcher())
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val slowProvider = FakeCloudStreamingProvider(firstTokenDelayMs = 6_000L)
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
        val job = this.launch {
            engine.streamSuggestion(makeRequest()).collect { events.add(it) }
        }
        advanceTimeBy(5_001L)
        job.join()

        val fallback = events.filterIsInstance<InferenceEvent.FallbackActivated>()
        assertEquals(1, fallback.size)
        assertEquals(FallbackReason.TIMEOUT, fallback.first().reason)
    }

    /** Contract 3.4: Per-suggestion retry — first call fallbacks, second call uses cloud */
    @Test
    fun fallback_perSuggestion_retriesCloudNextQuestion() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val connectivity = FakeConnectivityChecker(isAvailable = false)
        val fakeProvider = FakeCloudStreamingProvider(tokens = listOf("Hello"))
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = fakeProvider,
            claudeProviderFactory = { _ -> fakeProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = connectivity.asLambda
        )

        val firstEvents = engine.streamSuggestion(makeRequest()).toList()
        assertTrue(firstEvents.any { it is InferenceEvent.FallbackActivated })

        connectivity.isAvailable = true

        val secondEvents = engine.streamSuggestion(makeRequest()).toList()
        assertTrue(secondEvents.any { it is InferenceEvent.Token })
        assertFalse(secondEvents.any { it is InferenceEvent.FallbackActivated })
    }

    /** Contract 3.5: Auth error (401) disables cloud and updates connectionStatus */
    @Test
    fun fallback_authError_disablesCloud() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val authErrorProvider = FakeCloudStreamingProvider(
            throwOnStream = RuntimeException("401 Unauthorized — invalid API key")
        )
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = authErrorProvider,
            claudeProviderFactory = { _ -> authErrorProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = { true }
        )

        val events = engine.streamSuggestion(makeRequest()).toList()

        val fallback = events.filterIsInstance<InferenceEvent.FallbackActivated>()
        assertEquals(1, fallback.size)
        assertEquals(FallbackReason.AUTH_ERROR, fallback.first().reason)

        val config = configRepo.observe(CloudProvider.CLAUDE).first()
        assertEquals(false, config.connectionStatus)
    }

    /** Contract 3.6: Provider error (5xx) does NOT disable cloud */
    @Test
    fun fallback_providerError_doesNotDisableCloud() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        configRepo.updateConnectionStatus(CloudProvider.CLAUDE, connected = true)

        val providerErrorProvider = FakeCloudStreamingProvider(
            throwOnStream = RuntimeException("Service unavailable (503)")
        )
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = providerErrorProvider,
            claudeProviderFactory = { _ -> providerErrorProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = { true }
        )

        val events = engine.streamSuggestion(makeRequest()).toList()

        val fallback = events.filterIsInstance<InferenceEvent.FallbackActivated>()
        assertEquals(FallbackReason.PROVIDER_ERROR, fallback.first().reason)

        val config = configRepo.observe(CloudProvider.CLAUDE).first()
        assertEquals(true, config.connectionStatus)
    }

    /** Contract 3.7: Question text longer than 600 chars is truncated to 600 */
    @Test
    fun dataTruncation_questionOver600Chars() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val capturedProvider = FakeCloudStreamingProvider()
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = capturedProvider,
            claudeProviderFactory = { _ -> capturedProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = { true }
        )

        engine.streamSuggestion(makeRequest(questionText = "A".repeat(700))).toList()

        assertEquals(600, capturedProvider.lastRequest?.questionText?.length)
    }

    /** Contract 3.8: System prompt longer than 320 chars is truncated to 320 */
    @Test
    fun dataTruncation_systemPromptOver320Chars() = runTest(UnconfinedTestDispatcher()) {
        val (apiKeyStore, configRepo) = makeComponents()
        apiKeyStore.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val capturedProvider = FakeCloudStreamingProvider()
        val engine = CloudInferenceEngine(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            configRepository = configRepo,
            geminiProvider = capturedProvider,
            claudeProviderFactory = { _ -> capturedProvider },
            onDeviceFallback = fallbackProvider,
            connectivityChecker = { true }
        )

        engine.streamSuggestion(makeRequest(systemPrompt = "B".repeat(400))).toList()

        assertEquals(320, capturedProvider.lastRequest?.systemPrompt?.length)
    }
}
