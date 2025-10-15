package project.pipepipe.app.viewmodel

import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.DashboardUiState

class DashboardViewModel : BaseViewModel<DashboardUiState>(DashboardUiState()) {

    suspend fun loadDashboard() {
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val feedGroups = DatabaseOperations.getPinnedFeedGroups()
        val history = DatabaseOperations.loadStreamHistoryItems()
        val localPlaylists = DatabaseOperations.getPinnedPlaylists()
        setState {
            it.copy(
                common = it.common.copy(isLoading = false),
                feedGroups = feedGroups,
                historyItems = history,
                playlists = localPlaylists
            )
        }
    }
}
