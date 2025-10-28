package project.pipepipe.app.helper

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
}
