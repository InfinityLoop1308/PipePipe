package project.pipepipe.app.helper

object MainScreenTabDefaults {
    const val SUBSCRIPTIONS_ROUTE = "subscriptions"
    const val BOOKMARKED_PLAYLISTS_ROUTE = "bookmarked_playlists"

    fun getDefaultTabs(): List<String> = listOf(
        SUBSCRIPTIONS_ROUTE,
        BOOKMARKED_PLAYLISTS_ROUTE
    )
}
