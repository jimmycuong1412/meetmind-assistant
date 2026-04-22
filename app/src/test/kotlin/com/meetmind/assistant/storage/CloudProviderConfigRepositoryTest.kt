// T017: CloudProviderConfigRepository unit tests — Contract 2 (all 5 test cases)
package com.meetmind.assistant.storage

import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.helpers.FakeApiKeyStore
import com.meetmind.assistant.helpers.testDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class CloudProviderConfigRepositoryTest {

    private lateinit var apiKeyStore: FakeApiKeyStore

    // Each test gets a fresh TestScope + isolated DataStore to avoid state leaks
    private fun makeRepo(): Pair<CloudProviderConfigRepository, TestScope> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val ds = testDataStore(scope)
        val repo = CloudProviderConfigRepository(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            apiKeyStore = FakeApiKeyStore(),
            dataStore = ds
        )
        return Pair(repo, scope)
    }

    @Before
    fun setUp() {
        apiKeyStore = FakeApiKeyStore()
    }

    /** Contract 2.1: Fresh DataStore has no config — connectionStatus null, isEnabled false */
    @Test
    fun defaultConfig_isUnconfigured() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            val config = repo.observe(CloudProvider.GEMINI).first()
            assertNull(config.connectionStatus)
            assertFalse(config.isEnabled)
            assertNull(config.encryptedApiKey)
        }
    }

    /** Contract 2.2: updateConnectionStatus(connected=true) sets connectionStatus = true */
    @Test
    fun updateConnectionStatus_connected() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.updateConnectionStatus(CloudProvider.GEMINI, connected = true)
            val config = repo.observe(CloudProvider.GEMINI).first()
            assertEquals(true, config.connectionStatus)
        }
    }

    /** Contract 2.3: setEnabled(true) works when connectionStatus is already true */
    @Test
    fun setEnabled_true_whenConnected() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.updateConnectionStatus(CloudProvider.GEMINI, connected = true)
            repo.setEnabled(CloudProvider.GEMINI, enabled = true)
            val config = repo.observe(CloudProvider.GEMINI).first()
            assertTrue(config.isEnabled)
        }
    }

    /** Contract 2.4: setEnabled(true) when not connected throws IllegalStateException */
    @Test(expected = IllegalStateException::class)
    fun setEnabled_true_whenNotConnected_throws() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.setEnabled(CloudProvider.GEMINI, enabled = true)
        }
    }

    /**
     * Contract 2.5: updateConnectionStatus(connected=false) disables cloud and sets
     * connectionStatus = false, even when the provider was previously enabled.
     */
    @Test
    fun updateConnectionStatus_false_disablesCloud() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.updateConnectionStatus(CloudProvider.GEMINI, connected = true)
            repo.setEnabled(CloudProvider.GEMINI, enabled = true)

            val enabledConfig = repo.observe(CloudProvider.GEMINI).first()
            assertTrue("Pre-condition: should be enabled", enabledConfig.isEnabled)

            repo.updateConnectionStatus(CloudProvider.GEMINI, connected = false)

            val config = repo.observe(CloudProvider.GEMINI).first()
            assertFalse(config.isEnabled)
            assertEquals(false, config.connectionStatus)
        }
    }
}
