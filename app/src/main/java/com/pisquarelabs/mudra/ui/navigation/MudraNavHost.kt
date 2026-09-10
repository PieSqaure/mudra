package com.pisquarelabs.mudra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pisquarelabs.mudra.di.AppContainer
import com.pisquarelabs.mudra.ui.caption.CaptionScreen
import com.pisquarelabs.mudra.ui.caption.CaptionViewModel
import com.pisquarelabs.mudra.ui.caption.captionViewModelFactory
import com.pisquarelabs.mudra.ui.settings.SettingsScreen

private object Routes {
    const val CAPTION = "caption"
    const val SETTINGS = "settings"
}

@Composable
fun MudraNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Routes.CAPTION) {
        composable(Routes.CAPTION) {
            val viewModel: CaptionViewModel = viewModel(factory = captionViewModelFactory(container))
            CaptionScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                createAnalyzer = { onResult -> container.createHandLandmarkerAnalyzer(onResult) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsRepository = container.settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
