
package project.pipepipe.app.ui.screens

import com.squareup.wire.Service
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Dashboard : Screen("dashboard")
    object BookmarkedPlaylist : Screen("bookmarked_playlist")
    object PlaylistDetail : Screen("playlist?url={url}&serviceId={serviceId}")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Search : Screen("search")
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
}
