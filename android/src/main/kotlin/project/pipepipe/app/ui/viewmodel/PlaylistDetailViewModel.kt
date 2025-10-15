package project.pipepipe.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.uistate.PlaylistUiState
import project.pipepipe.app.uistate.PlaylistSortMode
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.app.viewmodel.PlaylistDetailViewModel as SharedPlaylistDetailViewModel

class PlaylistDetailViewModel : ViewModel() {
    private val sharedViewModel: SharedPlaylistDetailViewModel = SharedPlaylistDetailViewModel()
    val uiState: StateFlow<PlaylistUiState> = sharedViewModel.uiState

    fun loadPlaylist(url: String, serviceId: String? = null) {
        viewModelScope.launch {
            sharedViewModel.loadPlaylist(url, serviceId)
        }
    }

    fun updateSearchQuery(query: String) {
        viewModelScope.launch {
            sharedViewModel.updateSearchQuery(query)
        }
    }

    fun updateSortMode(sortMode: PlaylistSortMode) {
        viewModelScope.launch {
            sharedViewModel.updateSortMode(sortMode)
        }
    }

    fun updatePlaylistName(newName: String) {
        viewModelScope.launch {
            sharedViewModel.updatePlaylistName(newName)
        }
    }

    fun reorderItems(fromIndex: Int, toIndex: Int) {
        sharedViewModel.reorderItems(fromIndex, toIndex)
        viewModelScope.launch {
            uiState.value.list.itemList.let { items ->
                val orderedJoinIds = items.mapNotNull { it.joinId }
                DatabaseOperations.reorderPlaylistItem(orderedJoinIds)
            }
        }
    }

    fun removeItem(streamInfo: StreamInfo) {
        viewModelScope.launch {
            sharedViewModel.removeItem(streamInfo)
            if (uiState.value.playlistType == PlaylistType.LOCAL) {
                DatabaseOperations.removeStreamFromPlaylistByJoinId(streamInfo.joinId!!)
            } else if (uiState.value.playlistType == PlaylistType.HISTORY) {
                DatabaseOperations.deleteStreamHistory(streamInfo.url)
            }
        }
    }
    val sortedItems get() = sharedViewModel.sortedItems

    fun setRefreshing(isRefreshing: Boolean) {
        viewModelScope.launch {
            sharedViewModel.setRefreshing(isRefreshing)
        }
    }
    fun updateFeedLastUpdated(feedId: Long) {
        viewModelScope.launch {
            sharedViewModel.updateFeedLastUpdated(feedId)
        }
    }
}