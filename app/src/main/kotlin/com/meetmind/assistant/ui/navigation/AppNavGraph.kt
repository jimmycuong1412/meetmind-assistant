// T022: Navigation graph — wires all screens into the app nav
// T027 (spec 008): Added ANALYSIS_SETTINGS route
// spec 009 — T023: viewModel(factory=...) → hiltViewModel(); application param removed
// spec 009 — T049: Route guard — redirect SESSION → MODEL_SETUP if STT models not ready
package com.meetmind.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.meetmind.assistant.ui.screens.AnalysisSettingsScreen
import com.meetmind.assistant.ui.screens.CloudSettingsScreen
import com.meetmind.assistant.ui.screens.HomeScreen
import com.meetmind.assistant.ui.screens.ModelSetupScreen
import com.meetmind.assistant.ui.screens.SessionScreen
import com.meetmind.assistant.viewmodel.AnalysisSettingsViewModel
import com.meetmind.assistant.viewmodel.CloudSettingsViewModel
import com.meetmind.assistant.viewmodel.HomeViewModel
import com.meetmind.assistant.viewmodel.ModelSetupViewModel
import com.meetmind.assistant.viewmodel.SessionViewModel
import androidx.hilt.navigation.compose.hiltViewModel

object Routes {
    const val HOME = "home"
    const val SESSION = "session"
    const val CLOUD_SETTINGS = "cloud_settings"
    const val MODEL_SETUP = "settings/model"         // T034 (spec 007)
    const val ANALYSIS_SETTINGS = "settings/analysis" // T027 (spec 008)
}

@Composable
fun AppNavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            val homeVm: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = homeVm,
                onStartSession = { navController.navigate(Routes.SESSION) },
                onOpenCloudSettings = { navController.navigate(Routes.CLOUD_SETTINGS) },
                onOpenModelSetup = { navController.navigate(Routes.MODEL_SETUP) }
            )
        }

        composable(Routes.SESSION) {
            // spec 009 T049: Gate session start on STT model readiness.
            // ModelSetupViewModel is used to check isSttReady() without adding a new ViewModel type.
            val modelVm: ModelSetupViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                if (!modelVm.isSttReady()) {
                    navController.navigate(Routes.MODEL_SETUP) {
                        popUpTo(Routes.SESSION) { inclusive = true }
                    }
                }
            }
            val sessionVm: SessionViewModel = hiltViewModel()
            SessionScreen(
                viewModel = sessionVm,
                onEndSession = { navController.popBackStack() }
            )
        }

        // T034 (spec 007): On-Device Model setup screen route
        composable(Routes.MODEL_SETUP) {
            val modelVm: ModelSetupViewModel = hiltViewModel()
            ModelSetupScreen(
                viewModel = modelVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // T027 (spec 008): Conversation Analysis Settings screen route
        composable(Routes.ANALYSIS_SETTINGS) {
            val analysisVm: AnalysisSettingsViewModel = hiltViewModel()
            AnalysisSettingsScreen(
                viewModel = analysisVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // T022: Cloud AI Settings screen route
        composable(Routes.CLOUD_SETTINGS) {
            val cloudVm: CloudSettingsViewModel = hiltViewModel()
            CloudSettingsScreen(
                viewModel = cloudVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
