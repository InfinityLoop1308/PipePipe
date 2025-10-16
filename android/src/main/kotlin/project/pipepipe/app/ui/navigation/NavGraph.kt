package project.pipepipe.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import project.pipepipe.app.ui.screens.ChannelScreen
import project.pipepipe.app.ui.screens.PlaylistDetailScreen
import project.pipepipe.app.ui.screens.Screen
import project.pipepipe.app.ui.screens.SearchScreen
import project.pipepipe.app.ui.screens.SettingsScreen
import project.pipepipe.app.ui.screens.TabNavigationScreen
import project.pipepipe.app.ui.screens.settings.AppearanceSettingsScreen
import project.pipepipe.app.ui.screens.settings.ChannelNotificationSelectionScreen
import project.pipepipe.app.ui.screens.settings.FeedSettingsScreen
import project.pipepipe.app.ui.screens.settings.FilterByKeywordsScreen
import project.pipepipe.app.ui.screens.settings.FilterSettingScreen
import project.pipepipe.app.ui.screens.settings.GestureSettingScreen
import project.pipepipe.app.ui.screens.settings.ImportExportSettingScreen
import project.pipepipe.app.ui.screens.settings.LogSettingScreen
import project.pipepipe.app.ui.screens.settings.PlayerSettingScreen
import project.pipepipe.app.ui.screens.settings.SponsorBlockCategoriesSettingsScreen
import project.pipepipe.app.ui.screens.settings.SponsorBlockSettingsScreen

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Screen.Main.route,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable(Screen.Main.route) {
            TabNavigationScreen(navController = navController)
        }
        composable(Screen.History.route) {
            PlaylistDetailScreen(
                url = "local://history",
                navController = navController
            )
        }
        composable(Screen.PlaylistDetail.route) { backStackEntry ->
            PlaylistDetailScreen(
                url = backStackEntry.arguments!!.getString("url")!!,
                serviceId = backStackEntry.arguments!!.getString("serviceId"),
                navController = navController
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.GestureSettings.route) {
            GestureSettingScreen(navController = navController)
        }
        composable(Screen.PlayerSettings.route) {
            PlayerSettingScreen(navController = navController)
        }
        composable(Screen.FilterSettings.route) {
            FilterSettingScreen(navController = navController)
        }
        composable(Screen.FilterKeywordSettings.route) {
            FilterByKeywordsScreen(navController = navController)
        }
        composable(Screen.FilterChannelSettings.route) {
            FilterByKeywordsScreen(navController = navController, isChannelScreen = true)
        }
        composable(Screen.ImportExportSettings.route) {
            ImportExportSettingScreen(navController = navController)
        }
        composable(Screen.SponsorBlockSettings.route) {
            SponsorBlockSettingsScreen(navController = navController)
        }
        composable(Screen.SponsorBlockCategorySettings.route) {
            SponsorBlockCategoriesSettingsScreen(navController = navController)
        }
        composable(Screen.LogSettings.route) {
            LogSettingScreen(navController = navController)
        }
        composable(Screen.AppearanceSettings.route) {
            AppearanceSettingsScreen(navController = navController)
        }
        composable(Screen.FeedSettings.route) {
            FeedSettingsScreen(navController = navController)
        }
        composable(Screen.ChannelNotificationSelection.route) {
            ChannelNotificationSelectionScreen(navController = navController)
        }
        composable(Screen.Search.route) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query")
            val serviceId = backStackEntry.arguments?.getString("serviceId")
            SearchScreen(
                navController = navController,
                initialQuery = query,
                initialServiceId = serviceId
            )
        }
        composable(Screen.Channel.route) { backStackEntry ->
            val channelUrl = backStackEntry.arguments!!.getString("url")!!
            val serviceId = backStackEntry.arguments!!.getString("serviceId")!!
            ChannelScreen(navController = navController, channelUrl = channelUrl, serviceId = serviceId)
        }
        composable(Screen.Feed.route) { backStackEntry ->
            val feedId = backStackEntry.arguments!!.getString("id")!!
            val name = backStackEntry.arguments!!.getString("name")
            val encodedUrl = if (name == null) {
                "local://feed/$feedId"
            } else {
                "local://feed/$feedId?name=$name"
            }
            PlaylistDetailScreen(
                url = encodedUrl,
                navController = navController
            )
        }
    }
}