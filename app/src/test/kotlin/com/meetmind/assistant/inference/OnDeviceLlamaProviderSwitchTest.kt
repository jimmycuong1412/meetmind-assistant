package com.meetmind.assistant.inference

import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.helpers.FakeLlamaJni
import com.meetmind.assistant.helpers.testDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for [OnDeviceLlamaProvider] model switching behaviour.
 *
 * C5.1 — loadModel with different path forces unload then reload
 * C5.2 — loadModel with same path is idempotent (no unload)
 *
 * spec 009 — T028
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class OnDeviceLlamaProviderSwitchTest {

    private fun makeProvider(
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler
    ): Pair<OnDeviceLlamaProvider, FakeLlamaJni> {
        val scope = TestScope(StandardTestDispatcher(scheduler))
        val dataStore = testDataStore(scope)
        val configRepo = ModelConfigRepository(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            dataStore = dataStore
        )
        val fake = FakeLlamaJni()
        val provider = OnDeviceLlamaProvider(
            configRepository = configRepo,
            backend = fake,
            loadDispatcher = StandardTestDispatcher(scheduler)
        )
        return provider to fake
    }

    /**
     * C5.1 — If [OnDeviceLlamaProvider] has model A loaded and [loadModel] is called with a
     * different path, [unload()] must be called before the new model loads.
     * The provider must never hold two model handles simultaneously.
     */
    @Test
    fun `C5_1 loading different path forces unload first`() = runTest {
        val (provider, fake) = makeProvider(testScheduler)

        // Load first model — succeeds via FakeLlamaJni returning handle=1
        provider.loadModel("/models/q4km.gguf")
        assertTrue("Provider should be loaded after first loadModel", provider.isLoaded())

        // Load second model with different path
        provider.loadModel("/models/q8.gguf")

        assertEquals(
            "unload must be called once before second load",
            1,
            fake.unloadCallCount
        )
        assertTrue("Provider should remain loaded after path-change reload", provider.isLoaded())
    }

    /**
     * C5.2 — Calling [loadModel] twice with the same path must not call [unload()].
     * Idempotent: returns immediately if already loaded with the same path.
     */
    @Test
    fun `C5_2 loading same path is idempotent no unload`() = runTest {
        val (provider, fake) = makeProvider(testScheduler)

        // Load model once
        provider.loadModel("/models/q4km.gguf")
        assertTrue(provider.isLoaded())

        // Load with same path again
        provider.loadModel("/models/q4km.gguf")

        assertEquals("unload must NOT be called when path is unchanged", 0, fake.unloadCallCount)
        assertTrue(provider.isLoaded())
    }
}
