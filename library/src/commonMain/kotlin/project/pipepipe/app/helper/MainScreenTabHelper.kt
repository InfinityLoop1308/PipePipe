package project.pipepipe.app.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import project.pipepipe.app.MR
import java.net.URLDecoder
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import project.pipepipe.app.helper.StringResourceHelper.getTranslatedTrendingName

object MainScreenTabHelper {
    /**
     * Get display name for a tab based on its route
     */
    @Composable
    fun getTabDisplayName(route: String): String {
        val baseRoute = route.substringBefore('?')

        return when {
            route == "subscriptions" -> stringResource(MR.strings.tab_subscriptions)
            route == "bookmarked_playlists" -> stringResource(MR.strings.tab_bookmarks)
            route == "blank" -> "PipePipe"
            route == "history" -> stringResource(MR.strings.title_activity_history)
            route.startsWith("feed/-1") -> stringResource(MR.strings.all_feed_title)
            route.contains("name=") -> {
                val nameParam = route.substringAfter("name=").substringBefore('&')
                if (route.contains("url=trending")) {
                    getTranslatedTrendingName(nameParam)
                } else {
                    runCatching{ URLDecoder.decode(nameParam, "UTF-8") }.getOrDefault(stringResource(MR.strings.unknown))
                }
            }
            else -> route
        }
    }

    /**
     * Get icon for a tab based on its route
     */
    fun getTabIcon(route: String): ImageVector {
        return when {
            route.contains("url=trending") -> Icons.Default.Whatshot
            route == "subscriptions" -> Icons.Default.Subscriptions
            route == "bookmarked_playlists" || route.startsWith("playlist") -> Icons.Default.Bookmark
            route == "blank" -> Icons.Default.Tab
            route == "history" -> Icons.Default.History
            route.startsWith("channel") -> Icons.Default.Person
            route.startsWith("feed/") -> {
                // Extract iconId from route parameters
                val params = route.substringAfter('?', "")
                val iconIdParam = params.split('&').find { it.startsWith("iconId=") }?.substringAfter("iconId=")
                val iconId = iconIdParam?.toIntOrNull() ?: 0
                categoryIconFor(iconId)
            }
            else -> Icons.Default.Tab
        }
    }


    fun categoryIconFor(id: Int): ImageVector =
        when (id) {
            1 -> Icons.Default.MusicNote
            2 -> Icons.Default.School
            3 -> Icons.Default.FitnessCenter
            4 -> Icons.Default.SatelliteAlt
            5 -> Icons.Default.Computer
            6 -> Icons.Default.VideogameAsset
            7 -> Icons.Default.DirectionsBike
            8 -> Icons.Default.Campaign
            9 -> Icons.Default.Favorite
            10 -> Icons.Default.DirectionsCar
            11 -> Icons.Default.Motorcycle
            12 -> Icons.Default.TrendingUp
            13 -> Icons.Default.Movie
            14 -> Icons.Default.Backup
            15 -> Icons.Default.Palette
            16 -> Icons.Default.Person
            17 -> Icons.Default.People
            18 -> Icons.Default.AttachMoney
            19 -> Icons.Default.ChildCare
            20 -> Icons.Default.Fastfood
            21 -> Icons.Default.InsertEmoticon
            22 -> Icons.Default.Explore
            23 -> Icons.Default.Restaurant
            24 -> Icons.Default.Mic
            25 -> Icons.Default.Headset
            26 -> Icons.Default.Radio
            27 -> Icons.Default.ShoppingCart
            28 -> Icons.Default.WatchLater
            29 -> Icons.Default.Work
            30 -> Icons.Default.Whatshot
            31 -> Icons.Default.Tv
            32 -> Icons.Default.Bookmark
            33 -> Icons.Default.Pets
            34 -> Icons.Default.Public
            35 -> Icons.Default.Stars
            36 -> Icons.Default.WbSunny
            0, 37 -> Icons.Default.RssFeed
            38 -> Icons.Default.Subscriptions
            else -> Icons.Default.RssFeed
        }
}