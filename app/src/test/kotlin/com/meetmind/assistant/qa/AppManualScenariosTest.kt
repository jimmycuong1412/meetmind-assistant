package com.meetmind.assistant.qa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.meetmind.assistant.analysis.AnalysisCadenceController
import com.meetmind.assistant.analysis.TranscriptWindowBuffer
import com.meetmind.assistant.data.model.CloudBadgeState
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.helpers.*
import com.meetmind.assistant.inference.CloudInferenceEngine
import com.meetmind.assistant.storage.CloudProviderConfigRepository
import com.meetmind.assistant.ui.screens.HomeScreen
import com.meetmind.assistant.ui.theme.MeetMindTheme
import com.meetmind.assistant.viewmodel.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.content.Context

/**
 * Manual scenario tests for spec 009 Hilt-migrated app.
 *
 * Note: With Hilt these are now unit/integration tests using fakes directly rather
 * than overriding the AppContainer via reflection. Full @HiltAndroidTest Compose
 * tests require androidTest instrumentation; these lightweight Robolectric tests
 * test individual screens with fakes injected directly.
 *
 * spec 009 — T024 (test helper update)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class AppManualScenariosTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var fakeApiKeyStore: FakeApiKeyStore
    private lateinit var configRepo: CloudProviderConfigRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val scope = TestScope(testDispatcher)
        fakeApiKeyStore = FakeApiKeyStore()
        configRepo = CloudProviderConfigRepository(
            context = context,
            apiKeyStore = fakeApiKeyStore,
            dataStore = testDataStore(scope)
        )
    }

    private fun createHomeViewModel(): HomeViewModel =
        HomeViewModel(configRepository = configRepo)

    /** S1.1 — Cold launch lands on Home screen (HomeScreen renders correctly) */
    @Test
    fun s1_1_coldLaunchLandsOnHome() {
        val homeVm = createHomeViewModel()
        composeTestRule.setContent {
            MeetMindTheme {
                HomeScreen(
                    viewModel = homeVm,
                    onStartSession = {},
                    onOpenCloudSettings = {},
                    onOpenModelSetup = {}
                )
            }
        }

        composeTestRule.onNodeWithText("MeetMind").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Session").assertIsDisplayed()
    }

    /** S3.1 — Toggle is disabled when no key is configured */
    @Test
    fun s3_1_toggleDisabledWhenNoKey() {
        val homeVm = createHomeViewModel()
        composeTestRule.setContent {
            MeetMindTheme {
                HomeScreen(
                    viewModel = homeVm,
                    onStartSession = {},
                    onOpenCloudSettings = {},
                    onOpenModelSetup = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("cloud_mode_toggle", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("cloud_mode_toggle", useUnmergedTree = true).assertHasNoClickAction()
    }
}
