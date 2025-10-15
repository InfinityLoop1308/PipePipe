package project.pipepipe.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import project.pipepipe.shared.database.DatabaseOperations
import project.pipepipe.shared.uistate.BookmarkedPlaylistUiState
import project.pipepipe.shared.viewmodel.BookmarkedPlaylistViewModel

class BookmarkedPlaylistViewModel : ViewModel() {
    private val sharedViewModel = BookmarkedPlaylistViewModel()
    val uiState: StateFlow<BookmarkedPlaylistUiState> = sharedViewModel.uiState

    fun loadPlaylists() {
        viewModelScope.launch {
            sharedViewModel.loadPlaylists()
        }
    }

    fun reorderItems(fromIndex: Int, toIndex: Int) {
        sharedViewModel.reorderItems(fromIndex, toIndex)
        viewModelScope.launch {
            val allPlaylists = uiState.value.playlists
            allPlaylists.forEachIndexed { index, playlist ->
                if (playlist.serviceId == null) {
                    DatabaseOperations.updatePlaylistDisplayIndex(playlist.uid!!, index.toLong())
                } else {
                    DatabaseOperations.updateRemotePlaylistDisplayIndex(playlist.uid!!, index.toLong())
                }
            }
        }
    }
}
