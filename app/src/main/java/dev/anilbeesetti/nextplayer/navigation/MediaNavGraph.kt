package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.downloads.DownloadsScreen
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object MediaRootRoute

@Serializable
data object DownloadsRoute

fun NavHostController.navigateToDownloads() {
    navigate(DownloadsRoute)
}

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<MediaRootRoute>(startDestination = DownloadsRoute) {
        composable<DownloadsRoute> {
            DownloadsScreen(onNavigateUp = navController::navigateUp)
        }
    }
}
