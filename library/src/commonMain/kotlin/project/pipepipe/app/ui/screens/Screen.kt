package project.pipepipe.app.ui.screens

import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Dashboard : Screen("dashboard")
    object Subscription: Screen("subscription")
    object BookmarkedPlaylist : Screen("bookmarked_playlist")
    object PlaylistDetail : Screen("playlist?url={url}&serviceId={serviceId}") {
        fun createRoute(url: String, serviceId: String? = null): String {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            return if (serviceId != null) {
                "playlist?url=${encodedUrl}&serviceId=${serviceId}"
            } else {
                "playlist?url=${encodedUrl}"
            }
        }
    }

    object History : Screen("history")
    object Settings : Screen("settings")
    object Search : Screen("search?query={query}&serviceId={serviceId}") {
        fun createRoute(query: String? = null, serviceId: String? = null): String {
            return if (query != null && serviceId != null) {
                val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
                "search?query=${encodedQuery}&serviceId=${serviceId}"
            } else {
                "search"
            }
        }
    }
    object Channel : Screen("channel?url={url}&serviceId={serviceId}") {
        fun createRoute(url: String, serviceId: String): String {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            return "channel?url=${encodedUrl}&serviceId=${serviceId}"
        }
    }
    object Feed : Screen("local://feed/{id}?name={name}") {
        fun createRoute(id: Long, name: String?): String {
            if (name.isNullOrEmpty()) {
                return "local://feed/$id"
            }
            val encodedName = URLEncoder.encode(name, "UTF-8")
            return "local://feed/$id?name=$encodedName"
        }
    }
    object GestureSettings: Screen("gesture_settings")
    object PlayerSettings: Screen("player_settings")
    object ImportExportSettings: Screen("import_export_settings")
    object FilterSettings: Screen("filter_settings")
    object FilterKeywordSettings: Screen("filter_settings/keyword")
    object FilterChannelSettings: Screen("filter_settings/channel")
    object SponsorBlockSettings: Screen("sponsorblock_settings")
    object SponsorBlockCategorySettings: Screen("sponsorblock_settings/category")
    object LogSettings: Screen("log_settings")
    object AppearanceSettings: Screen("appearance_settings")
    object FeedSettings: Screen("feed_settings")
    object ChannelNotificationSelection: Screen("channel_notification_selection")
    object TabCustomization: Screen("tab_customization")
    object UpdateSettings: Screen("update_settings")
    object AboutSettings: Screen("about_settings")
    object Download : Screen("download")
}