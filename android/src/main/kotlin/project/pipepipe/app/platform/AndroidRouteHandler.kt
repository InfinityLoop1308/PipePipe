package project.pipepipe.app.platform

import androidx.compose.runtime.Composable
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.screens.DownloadScreen
import project.pipepipe.app.ui.screens.Screen

/**
 * Android implementation of PlatformRouteHandler.
 * Handles Android-specific screens like download and account settings.
 */
class AndroidRouteHandler : PlatformRouteHandler {
    @Composable
    override fun handleDownloadRoute(): Boolean {
        DownloadScreen(navController = SharedContext.navController)
        return true
    }

    @Composable
    override fun handleAccountSettingsRoute(): Boolean {
        // AccountSettingsScreen is in androidMain
        project.pipepipe.app.ui.screens.AccountSettingsScreen(
            navController = SharedContext.navController
        )
        return true
    }
}
