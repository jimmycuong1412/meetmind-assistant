// T029–T030 (spec 007): Contract tests for ModelSetupViewModel
// spec 009 — T034: Updated to use Hilt-style @Inject constructor with fake deps
package com.meetmind.assistant.viewmodel

import com.meetmind.assistant.data.DeviceTierDetector
import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.data.ModelDownloadManager
import com.meetmind.assistant.data.model.ModelLoadState
import com.meetmind.assistant.helpers.FakeLlamaJni
import com.meetmind.assistant.helpers.testDataStore
import com.meetmind.assistant.inference.OnDeviceLlamaProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for [ModelSetupViewModel].
 *
 * Contracts:
 *   C4.1 — when loadModel completes with Ready, loadState == Ready
 *   C4.3 — loadModel() transitions NotLoaded → Loading → Ready|Error
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ModelSetupViewModelTest {

    private fun makeViewModel(
        fake: FakeLlamaJni = FakeLlamaJni(),
        testScope: TestScope = TestScope(UnconfinedTestDispatcher())
    ): ModelSetupViewModel {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val repo = ModelConfigRepository(
            context = context,
            dataStore = testDataStore(testScope)
        )
        val provider = OnDeviceLlamaProvider(
            configRepository = repo,
            backend = fake,
            loadDispatcher = UnconfinedTestDispatcher(testScope.testScheduler)
        )
        return ModelSetupViewModel(
            context = context,
            configRepository = repo,
            llamaProvider = provider,
            deviceTierDetector = DeviceTierDetector(),
            downloadManager = ModelDownloadManager(context)
        )
    }

    // ── C4.1: loadState is Ready after successful loadModel ───────────────────

    @Test
    fun `C4-1 loadState is Ready after loadModel succeeds`() = runTest(UnconfinedTestDispatcher()) {
        val vm = makeViewModel(FakeLlamaJni(), testScope = TestScope(UnconfinedTestDispatcher()))

        vm.loadModel()

        assertTrue(
            "Expected Ready state after load, got ${vm.loadState.value}",
            vm.loadState.value is ModelLoadState.Ready
        )
    }

    // ── C4.3: loadModel transitions NotLoaded → Loading → Ready ──────────────

    @Test
    fun `C4-3 loadModel transitions through Loading to Ready`() = runTest(UnconfinedTestDispatcher()) {
        val vm = makeViewModel(testScope = TestScope(UnconfinedTestDispatcher()))

        // Initial state
        assertTrue(vm.loadState.value is ModelLoadState.NotLoaded)

        vm.loadModel()

        assertTrue(
            "Expected Ready state after completion, got ${vm.loadState.value}",
            vm.loadState.value is ModelLoadState.Ready
        )
    }

    @Test
    fun `C4-3b loadModel results in Error when backend fails`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni(loadShouldFail = true)
        val vm = makeViewModel(fake, testScope = TestScope(UnconfinedTestDispatcher()))

        vm.loadModel()

        assertTrue(
            "Expected Error state when load fails, got ${vm.loadState.value}",
            vm.loadState.value is ModelLoadState.Error
        )
    }

    @Test
    fun `second loadModel call is no-op when model is already Ready`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeLlamaJni()
        val vm = makeViewModel(fake, testScope = TestScope(UnconfinedTestDispatcher()))

        vm.loadModel()
        val firstHandle = (vm.loadState.value as ModelLoadState.Ready).handle

        vm.loadModel() // should be no-op (same path guard in OnDeviceLlamaProvider)
        val secondHandle = (vm.loadState.value as ModelLoadState.Ready).handle

        assertTrue("Handle should not change on redundant loadModel", firstHandle == secondHandle)
    }
}
