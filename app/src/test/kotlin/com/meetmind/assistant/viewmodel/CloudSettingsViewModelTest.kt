// T018: CloudSettingsViewModel unit tests — Contract 6 (all 4 test cases)
package com.meetmind.assistant.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.helpers.FakeApiKeyStore
import com.meetmind.assistant.helpers.FakeCloudKeyValidationService
import com.meetmind.assistant.helpers.testDataStore
import com.meetmind.assistant.inference.ValidationResult
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class CloudSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Returns a fresh (apiKeyStore, configRepo) pair backed by an isolated TestScope DataStore. */
    private fun makeComponents(): Pair<FakeApiKeyStore, CloudProviderConfigRepository> {
        val scope = TestScope(testDispatcher)
        val apiKeyStore = FakeApiKeyStore()
        val repo = CloudProviderConfigRepository(
            context = ApplicationProvider.getApplicationContext(),
            apiKeyStore = apiKeyStore,
            dataStore = testDataStore(scope)
        )
        return Pair(apiKeyStore, repo)
    }

    /** Contract 6.1: Valid key → validationState == Success, connectionStatus == true */
    @Test
    fun saveKey_valid_setsConnected() = runTest(testDispatcher) {
        val (apiKeyStore, configRepo) = makeComponents()
        val fakeValidation = FakeCloudKeyValidationService(ValidationResult.SUCCESS)
        val vm = CloudSettingsViewModel(apiKeyStore, configRepo, fakeValidation)

        vm.onSaveKey(CloudProvider.CLAUDE, "sk-ant-valid-key")
        advanceUntilIdle()

        assertEquals(ValidationState.Success, vm.validationState.value)
        // Assert via repo (source of truth) — avoids WhileSubscribed StateFlow timing issue
        assertEquals(true, configRepo.observe(CloudProvider.CLAUDE).first().connectionStatus)

        vm.viewModelScope.cancel()
    }

    /** Contract 6.2: Invalid key → validationState == Failure, key NOT stored */
    @Test
    fun saveKey_invalid_setsError() = runTest(testDispatcher) {
        val (apiKeyStore, configRepo) = makeComponents()
        val fakeValidation = FakeCloudKeyValidationService(ValidationResult.INVALID_KEY)
        val vm = CloudSettingsViewModel(apiKeyStore, configRepo, fakeValidation)

        vm.onSaveKey(CloudProvider.CLAUDE, "bad-key")
        advanceUntilIdle()

        assertEquals(ValidationState.Failure(ValidationResult.INVALID_KEY), vm.validationState.value)
        assertFalse(apiKeyStore.hasKey(CloudProvider.CLAUDE))

        vm.viewModelScope.cancel()
    }

    /** Contract 6.3: Delete key → encryptedApiKey null, connectionStatus null */
    @Test
    fun deleteKey_clearsConfig() = runTest(testDispatcher) {
        val (apiKeyStore, configRepo) = makeComponents()
        val fakeValidation = FakeCloudKeyValidationService(ValidationResult.SUCCESS)
        val vm = CloudSettingsViewModel(apiKeyStore, configRepo, fakeValidation)

        vm.onSaveKey(CloudProvider.CLAUDE, "sk-ant-to-delete")
        advanceUntilIdle()
        assertEquals(ValidationState.Success, vm.validationState.value)

        vm.onDeleteKey(CloudProvider.CLAUDE)
        advanceUntilIdle()

        val config = configRepo.observe(CloudProvider.CLAUDE).first()
        assertNull(config.encryptedApiKey)
        assertNull(config.connectionStatus)
        assertFalse(config.isEnabled)
        assertEquals(ValidationState.Idle, vm.validationState.value)

        vm.viewModelScope.cancel()
    }

    /** Contract 6.4: Auth error during stream disables cloud (handled via configRepository) */
    @Test
    fun authError_during_session_disablesCloud() = runTest(testDispatcher) {
        val (apiKeyStore, configRepo) = makeComponents()
        val fakeValidation = FakeCloudKeyValidationService(ValidationResult.SUCCESS)
        val vm = CloudSettingsViewModel(apiKeyStore, configRepo, fakeValidation)

        vm.onSaveKey(CloudProvider.CLAUDE, "sk-ant-connected-key")
        advanceUntilIdle()
        configRepo.setEnabled(CloudProvider.CLAUDE, enabled = true)

        // Simulate 401 received during inference — CloudInferenceEngine calls this
        configRepo.updateConnectionStatus(CloudProvider.CLAUDE, connected = false)
        advanceUntilIdle()

        val config = configRepo.observe(CloudProvider.CLAUDE).first()
        assertEquals(false, config.connectionStatus)
        assertFalse(config.isEnabled)

        vm.viewModelScope.cancel()
    }
}
