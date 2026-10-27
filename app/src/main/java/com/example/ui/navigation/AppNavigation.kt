package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.MediaRepository
import com.example.ui.components.BubbleNavTab
import com.example.ui.screens.AdminConfigScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.DriveScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodels.DriveViewModel
import com.example.ui.viewmodels.GalleryViewModel
import com.example.ui.viewmodels.SettingsViewModel

object NavRoutes {
    const val GALLERY = "gallery"
    const val DRIVE = "drive"
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val ADMIN_CONFIG = "admin_config"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }

    fun navigateToTab(tab: BubbleNavTab) {
        val targetRoute = when (tab) {
            BubbleNavTab.GALLERY -> NavRoutes.GALLERY
            BubbleNavTab.DRIVE -> NavRoutes.DRIVE
            BubbleNavTab.CAMERA -> NavRoutes.CAMERA
            BubbleNavTab.SETTINGS -> NavRoutes.SETTINGS
        }
        navController.navigate(targetRoute) {
            popUpTo(NavRoutes.GALLERY) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.GALLERY,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(
            route = NavRoutes.GALLERY,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val galleryVm = viewModel { GalleryViewModel(context, repository) }
            GalleryScreen(
                viewModel = galleryVm,
                onNavigateToTab = { navigateToTab(it) }
            )
        }

        composable(
            route = NavRoutes.DRIVE,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val driveVm = viewModel { DriveViewModel(context, repository) }
            DriveScreen(
                viewModel = driveVm,
                onNavigateToTab = { navigateToTab(it) }
            )
        }

        composable(
            route = NavRoutes.CAMERA,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            CameraScreen(
                repository = repository,
                onNavigateToTab = { navigateToTab(it) },
                onOpenGallery = { navigateToTab(BubbleNavTab.GALLERY) }
            )
        }

        composable(
            route = NavRoutes.SETTINGS,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            val settingsVm = viewModel { SettingsViewModel(context, repository) }
            SettingsScreen(
                viewModel = settingsVm,
                onNavigateToTab = { navigateToTab(it) },
                onNavigateToAdminConfig = { navController.navigate(NavRoutes.ADMIN_CONFIG) }
            )
        }

        composable(
            route = NavRoutes.ADMIN_CONFIG,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            AdminConfigScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
