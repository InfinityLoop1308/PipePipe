package project.pipepipe.app.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific route handler.
 * Allows Android to handle Android-only screens like download and account settings.
 * Other platforms can return false to indicate route not supported.
 */
interface PlatformRouteHandler {
    /**
     * Handle the download route.
     * @return true if route was handled, false if not supported
     */
    @Composable
    fun handleDownloadRoute(): Boolean

    /**
     * Handle the account settings route.
     * @return true if route was handled, false if not supported
     */
    @Composable
    fun handleAccountSettingsRoute(): Boolean
}
