package project.pipepipe.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import project.pipepipe.app.viewmodel.ChannelViewModel as SharedChannelViewModel
import project.pipepipe.app.uistate.ChannelUiState
import project.pipepipe.shared.infoitem.ChannelInfo

class ChannelViewModel : ViewModel() {
    private val sharedViewModel = SharedChannelViewModel()
    val uiState: StateFlow<ChannelUiState> = sharedViewModel.uiState

    fun loadChannelMainTab(url: String, serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadChannelMainTab(url, serviceId)
        }
    }

    fun loadMainTabMoreItems(serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadMainTabMoreItems(serviceId)
        }
    }

    fun loadChannelLiveTab(url: String, serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadChannelLiveTab(url, serviceId)
        }
    }

    fun loadLiveTabMoreItems(serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadLiveTabMoreItems(serviceId)
        }
    }

    fun loadChannelShortsTab(url: String, serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadChannelShortsTab(url, serviceId)
        }
    }

    fun loadShortsTabMoreItems(serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadShortsTabMoreItems(serviceId)
        }
    }


    fun loadChannelPlaylistTab(url: String, serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadChannelPlaylistTab(url, serviceId)
        }
    }

    fun loadPlaylistTabMoreItems(serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadPlaylistTabMoreItems(serviceId)
        }
    }

    fun loadChannelAlbumTab(url: String, serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadChannelAlbumTab(url, serviceId)
        }
    }

    fun loadAlbumTabMoreItems(serviceId: Int) {
        viewModelScope.launch {
            sharedViewModel.loadAlbumTabMoreItems(serviceId)
        }
    }

    fun toggleSubscription(channelInfo: ChannelInfo) {
        viewModelScope.launch {
            sharedViewModel.toggleSubscription(channelInfo)
        }
    }

    fun updateFeedGroups(channelInfo: ChannelInfo, selectedGroupIds: Set<Long>) {
        viewModelScope.launch {
            sharedViewModel.updateFeedGroups(channelInfo, selectedGroupIds)
        }
    }

    fun checkSubscriptionStatus(url: String) {
        viewModelScope.launch {
            sharedViewModel.checkSubscriptionStatus(url)
        }
    }
}
