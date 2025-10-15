package project.pipepipe.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import project.pipepipe.shared.viewmodel.ChannelViewModel as SharedChannelViewModel
import project.pipepipe.shared.uistate.ChannelUiState
import project.pipepipe.shared.infoitem.ChannelInfo

class ChannelViewModel : ViewModel() {
    private val sharedViewModel = SharedChannelViewModel()
    val uiState: StateFlow<ChannelUiState> = sharedViewModel.uiState

    fun loadChannelMainTab(url: String, serviceId: String) {
        viewModelScope.launch {
            sharedViewModel.loadChannelMainTab(url, serviceId)
        }
    }

    fun loadMainTabMoreItems(serviceId: String) {
        viewModelScope.launch {
            sharedViewModel.loadMainTabMoreItems(serviceId)
        }
    }

    fun loadChannelLiveTab(url: String, serviceId: String) {
        viewModelScope.launch {
            sharedViewModel.loadChannelLiveTab(url, serviceId)
        }
    }

    fun loadLiveTabMoreItems(serviceId: String) {
        viewModelScope.launch {
            sharedViewModel.loadLiveTabMoreItems(serviceId)
        }
    }

    fun loadChannelPlaylistTab(url: String, serviceId: String) {
        viewModelScope.launch {
            sharedViewModel.loadChannelPlaylistTab(url, serviceId)
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
