package project.pipepipe.app.helper

import kotlinx.serialization.json.Json
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.screens.Screen
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.ExternalUrlType

object ExternalUrlPatternHelper {
    /**
     * Matches a URL against all service URL patterns and returns the matching service ID and URL type.
     *
     * @param url The URL to match
     * @param services List of SupportedServiceInfo to check against
     * @return Pair of (serviceId, UrlType) if a match is found, null otherwise
     */
    fun matchUrl(url: String, services: List<SupportedServiceInfo>): Pair<String, ExternalUrlType>? {
        for (service in services) {
            for ((urlType, patterns) in service.urlPatterns) {
                for (pattern in patterns) {
                    if (url.contains(Regex(pattern))) {
                        return Pair(service.serviceId, urlType)
                    }
                }
            }
        }
        return null
    }

    /**
     * Tries to handle a URL by matching it against service patterns and navigating to the appropriate screen.
     * Uses SharedContext for accessing settings and navigation.
     *
     * @param url The URL to handle
     * @return true if the URL was successfully matched and handled, false otherwise
     */
    fun tryHandleUrl(url: String): Boolean {
        // Get supported services from SharedContext
        val jsonString = SharedContext.settingsManager.getString("supported_services", "[]")
        val services = Json.decodeFromString<List<SupportedServiceInfo>>(jsonString)

        // Try to match the URL
        val result = matchUrl(url, services) ?: return false

        // Navigate based on URL type
        val (serviceId, urlType) = result
        when (urlType) {
            ExternalUrlType.STREAM -> {
                SharedContext.sharedVideoDetailViewModel.loadVideoDetails(url, serviceId)
            }
            ExternalUrlType.CHANNEL -> {
                SharedContext.navController.navigate(
                    Screen.Channel.createRoute(url, serviceId)
                )
            }
            ExternalUrlType.PLAYLIST -> {
                SharedContext.navController.navigate(
                    Screen.PlaylistDetail.createRoute(url, serviceId)
                )
            }
        }
        return true
    }
}
