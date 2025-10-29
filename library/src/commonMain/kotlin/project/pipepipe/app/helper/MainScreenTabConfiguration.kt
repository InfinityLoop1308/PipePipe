package project.pipepipe.app.helper

import kotlinx.serialization.Serializable

@Serializable
data class MainScreenTabConfig(
    val route: String,
    val isDefault: Boolean = false
)

object MainScreenTabConfigDefaults {
    const val DASHBOARD_ROUTE = "dashboard"
    const val SUBSCRIPTIONS_ROUTE = "subscriptions"
    const val BOOKMARKED_PLAYLISTS_ROUTE = "bookmarked_playlists"

    fun getDefaultTabs(): List<MainScreenTabConfig> = listOf(
        MainScreenTabConfig(DASHBOARD_ROUTE, isDefault = true),
        MainScreenTabConfig(SUBSCRIPTIONS_ROUTE, isDefault = true),
        MainScreenTabConfig(BOOKMARKED_PLAYLISTS_ROUTE, isDefault = true)
    )
}
