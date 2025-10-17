package project.pipepipe.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.FilterHelper
import project.pipepipe.app.uistate.ChannelUiState
import project.pipepipe.app.uistate.ErrorInfo
import project.pipepipe.app.uistate.ListUiState
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.helper.executeJobFlow

class ChannelViewModel : BaseViewModel<ChannelUiState>(ChannelUiState()) {

    suspend fun loadChannelMainTab(url: String, serviceId: String) {
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_INFO,
                url,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        // Apply filters
        val rawItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
        val (filteredItems, _) = FilterHelper.filterStreamInfoList(
            rawItems,
            FilterHelper.FilterScope.CHANNELS
        )

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                channelInfo = result.info!! as ChannelInfo,
                videoTab = ListUiState(
                    itemList = filteredItems,
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
        DatabaseOperations.insertOrUpdateSubscription(result.info!! as ChannelInfo, true)
        DatabaseOperations.updateSubscriptionFeed((result.info as ChannelInfo).url, rawItems)
    }

    suspend fun loadMainTabMoreItems(serviceId: String) {
        val nextUrl = uiState.value.videoTab.nextPageUrl ?: return
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_GIVEN_PAGE,
                nextUrl,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        // Apply filters
        val rawItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
        val (filteredItems, _) = FilterHelper.filterStreamInfoList(
            rawItems,
            FilterHelper.FilterScope.CHANNELS
        )

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                videoTab = it.videoTab.copy(
                    itemList = it.videoTab.itemList + filteredItems,
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }

    suspend fun loadChannelLiveTab(url: String, serviceId: String) {
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_FIRST_PAGE,
                url,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        // Apply filters
        val rawItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
        val (filteredItems, _) = FilterHelper.filterStreamInfoList(
            rawItems,
            FilterHelper.FilterScope.CHANNELS
        )

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                liveTab = ListUiState(
                    itemList = filteredItems,
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }

    suspend fun loadLiveTabMoreItems(serviceId: String) {
        val nextUrl = uiState.value.liveTab.nextPageUrl ?: return
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_GIVEN_PAGE,
                nextUrl,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        // Apply filters
        val rawItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
        val (filteredItems, _) = FilterHelper.filterStreamInfoList(
            rawItems,
            FilterHelper.FilterScope.CHANNELS
        )

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                liveTab = it.liveTab.copy(
                    itemList = it.liveTab.itemList + filteredItems,
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }

    suspend fun loadChannelPlaylistTab(url: String, serviceId: String) {
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_FIRST_PAGE,
                url,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                playlistTab = ListUiState(
                    itemList = (result.pagedData?.itemList as? List<PlaylistInfo>).orEmpty(),
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }

    suspend fun loadPlaylistTabMoreItems(serviceId: String) {
        val nextUrl = uiState.value.playlistTab.nextPageUrl ?: return
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_GIVEN_PAGE,
                nextUrl,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                playlistTab = it.playlistTab.copy(
                    itemList = it.playlistTab.itemList + (result.pagedData?.itemList as? List<PlaylistInfo>).orEmpty(),
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }

    suspend fun loadChannelAlbumTab(url: String, serviceId: String) {
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_FIRST_PAGE,
                url,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                albumTab = ListUiState(
                    itemList = (result.pagedData?.itemList as? List<PlaylistInfo>).orEmpty(),
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }

    suspend fun loadAlbumTabMoreItems(serviceId: String) {
        val nextUrl = uiState.value.albumTab.nextPageUrl ?: return
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_GIVEN_PAGE,
                nextUrl,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                    )
                )
            }
            return
        }

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                albumTab = it.albumTab.copy(
                    itemList = it.albumTab.itemList + (result.pagedData?.itemList as? List<PlaylistInfo>).orEmpty(),
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }

    suspend fun toggleSubscription(channelInfo: ChannelInfo) {
        val isCurrentlySubscribed = DatabaseOperations.isSubscribed(channelInfo.url)

        if (isCurrentlySubscribed) {
            DatabaseOperations.deleteSubscription(channelInfo.url)
        } else {
            DatabaseOperations.insertOrUpdateSubscription(channelInfo, false)
        }

        // Update UI state
        setState {
            it.copy(isSubscribed = !isCurrentlySubscribed)
        }
    }

    suspend fun updateFeedGroups(channelInfo: ChannelInfo, selectedGroupIds: Set<Long>) {
        // Ensure subscription exists
        var subscription = DatabaseOperations.getSubscriptionByUrl(channelInfo.url)
        if (subscription == null) {
            DatabaseOperations.insertOrUpdateSubscription(channelInfo, false)
            subscription = DatabaseOperations.getSubscriptionByUrl(channelInfo.url)!!
        }

        val currentGroups = DatabaseOperations.getFeedGroupsBySubscription(subscription.uid).toSet()

        // Remove groups that are no longer selected
        currentGroups.subtract(selectedGroupIds).forEach { groupId ->
            DatabaseOperations.deleteFeedGroupSubscription(groupId, subscription.uid)
        }

        // Add newly selected groups
        selectedGroupIds.subtract(currentGroups).forEach { groupId ->
            DatabaseOperations.insertFeedGroupSubscription(groupId, subscription.uid)
        }

        setState {
            it.copy(isSubscribed = true)
        }
    }

    suspend fun checkSubscriptionStatus(url: String) {
        val isSubscribed = DatabaseOperations.isSubscribed(url)
        setState {
            it.copy(isSubscribed = isSubscribed)
        }
    }
}
