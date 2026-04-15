// T022: Navigation graph — wires all screens into the app nav
package com.meetmind.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.meetmind.assistant.MeetMindApplication
import com.meetmind.assistant.ui.screens.CloudSettingsScreen
import com.meetmind.assistant.ui.screens.HomeScreen
import com.meetmind.assistant.ui.screens.SessionScreen
import com.meetmind.assistant.viewmodel.CloudSettingsViewModel
import com.meetmind.assistant.viewmodel.HomeViewModel
import com.meetmind.assistant.viewmodel.SessionViewModel

object Routes {
    const val HOME = "home"
    const val SESSION = "session"
    const val CLOUD_SETTINGS = "cloud_settings"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    application: MeetMindApplication
) {
    val container = application.container

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            val homeVm: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(container.cloudProviderConfigRepository)
            )
            HomeScreen(
                viewModel = homeVm,
                onStartSession = { navController.navigate(Routes.SESSION) },
                onOpenCloudSettings = { navController.navigate(Routes.CLOUD_SETTINGS) }
            )
        }

        composable(Routes.SESSION) {
            val sessionVm: SessionViewModel = viewModel(
                factory = SessionViewModel.Factory(
                    cloudInferenceEngine = container.cloudInferenceEngine,
                    configRepository = container.cloudProviderConfigRepository
                )
            )
            SessionScreen(
                viewModel = sessionVm,
                onEndSession = { navController.popBackStack() }
            )
        }

        // T022: Cloud AI Settings screen route
        composable(Routes.CLOUD_SETTINGS) {
            val cloudVm: CloudSettingsViewModel = viewModel(
                factory = CloudSettingsViewModel.Factory(
                    apiKeyStore = container.apiKeyStore,
                    configRepository = container.cloudProviderConfigRepository,
                    validationService = container.cloudKeyValidationService
                )
            )
            CloudSettingsScreen(
                viewModel = cloudVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
