package project.pipepipe.app.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.BookmarkedPlaylistUiState

class BookmarkedPlaylistViewModel : BaseViewModel<BookmarkedPlaylistUiState>(BookmarkedPlaylistUiState()) {

    fun loadPlaylists() {
        viewModelScope.launch {
            if (uiState.value.playlists.isEmpty()) {
                setState {
                    it.copy(
                        common = it.common.copy(isLoading = true)
                    )
                }
            }

            val playlists = DatabaseOperations.getAllPlaylistsCombined()
            setState {
                it.copy(
                    common = it.common.copy(isLoading = false),
                    playlists = playlists
                )
            }
        }
    }

    fun reorderItems(fromIndex: Int, toIndex: Int) {
        val items = uiState.value.playlists.toMutableList()
        val item = items.removeAt(fromIndex)
        items.add(toIndex, item)
        setState {
            it.copy(
                playlists = items
            )
        }
        // Persist the new order to database
        viewModelScope.launch {
            items.forEachIndexed { index, playlist ->
                if (playlist.serviceId == null) {
                    DatabaseOperations.updatePlaylistDisplayIndex(playlist.uid!!, index.toLong())
                } else {
                    DatabaseOperations.updateRemotePlaylistDisplayIndex(playlist.uid!!, index.toLong())
                }
            }
        }
    }
}
