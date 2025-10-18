package project.pipepipe.app.viewmodel

import kotlinx.serialization.json.Json
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.DashboardUiState
import project.pipepipe.shared.infoitem.SupportedServiceInfo

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

        // Load trending items from all services
        val trendingItems = try {
            val jsonString = SharedContext.settingsManager.getString("supported_services")
            val serviceInfoList = Json.decodeFromString<List<SupportedServiceInfo>>(jsonString)
            serviceInfoList.flatMap { it.trendingList }
        } catch (e: Exception) {
            emptyList()
        }

        setState {
            it.copy(
                common = it.common.copy(isLoading = false),
                feedGroups = feedGroups,
                historyItems = history,
                playlists = localPlaylists,
                trendingItems = trendingItems
            )
        }
    }
}
