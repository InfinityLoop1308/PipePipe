package project.pipepipe.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.ErrorInfo
import project.pipepipe.app.uistate.ListUiState
import project.pipepipe.app.uistate.PlaylistSortMode
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.app.uistate.PlaylistUiState
import project.pipepipe.app.SharedContext
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.helper.executeJobFlow
import kotlin.collections.plus

class PlaylistDetailViewModel : BaseViewModel<PlaylistUiState>(PlaylistUiState()) {

    suspend fun loadPlaylist(url: String, serviceId: String? = null) {
        val sortMode = runCatching{ PlaylistSortMode.valueOf(SharedContext.settingsManager.getString("playlist_sort_mode_key")) }
            .getOrDefault(PlaylistSortMode.ORIGIN)
        when {
            url.startsWith("local://playlist") -> {
                val playlistId = url.substringAfterLast("/")
                val playlistInfo = DatabaseOperations.getPlaylistInfoById(playlistId)
                val streamInfos = DatabaseOperations.loadPlaylistsItemsFromDatabase(playlistId)
                setState { state ->
                    state.copy(
                        common = state.common.copy(isLoading = false),
                        playlistType = PlaylistType.LOCAL,
                        playlistInfo = playlistInfo!!,
                        list = state.list.copy(itemList = streamInfos),
                        sortMode = sortMode
                    )
                }
                setState {
                    it.copy(displayItems = sortedItems)
                }
            }
            url == "local://history" -> {
                val streamInfos = DatabaseOperations.loadStreamHistoryItems()
                setState { state ->
                    state.copy(
                        common = state.common.copy(isLoading = false),
                        playlistType = PlaylistType.HISTORY,
                        playlistInfo = PlaylistInfo(
                            url = "local://history",
                            name = "History"
                        ),
                        list = state.list.copy(itemList = streamInfos),
                        displayItems = streamInfos,
                    )
                }
            }
            url.startsWith("local://feed") -> {
                val name = url.substringAfter("?name=","All")
                val feedId = url.substringAfterLast("/").substringBefore("?").toLong()
                val streamInfos = DatabaseOperations.getFeedStreamsByGroup(feedId)
                val isPinned = DatabaseOperations.getPinnedFeedGroups().any { it.uid == feedId }
                setState { state ->
                    state.copy(
                        common = state.common.copy(isLoading = false),
                        playlistType = PlaylistType.FEED,
                        playlistInfo = PlaylistInfo(
                            url = url,
                            name = name,
                            isPinned = isPinned
                        ),
                        list = state.list.copy(itemList = streamInfos),
                        displayItems = streamInfos
                    )
                }
                updateFeedLastUpdated(feedId)
            }
            else -> {
                loadRemotePlaylistDetail(url, serviceId!!)
            }
        }
    }

    suspend fun loadRemotePlaylistDetail(url: String, serviceId: String) {
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

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                playlistInfo = result.info as PlaylistInfo,
                playlistType = PlaylistType.REMOTE,
                list = ListUiState(
                    itemList = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty(),
                    nextPageUrl = result.pagedData?.nextPageUrl
                ),
                displayItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
            )
        }
    }

    suspend fun loadRemotePlaylistMoreItems(serviceId: String) {
        val nextUrl = uiState.value.list.nextPageUrl ?: return
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
                list = it.list.copy(
                    itemList = it.list.itemList + (result.pagedData?.itemList as? List<StreamInfo>).orEmpty(),
                    nextPageUrl = result.pagedData?.nextPageUrl
                ),
                displayItems = it.list.itemList + (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
            )
        }
    }

    fun updateSearchQuery(query: String) {
        setState { currentState ->
            currentState.copy(
                searchQuery = query
            )
        }
        updateDisplayItems()
    }

    fun updateSortMode(sortMode: PlaylistSortMode) {
        SharedContext.settingsManager.putString("playlist_sort_mode_key", sortMode.name)
        setState { currentState ->
            currentState.copy(sortMode = sortMode)
        }
        updateDisplayItems()
    }

    fun reorderItems(fromIndex: Int, toIndex: Int){
        val playlistItems = uiState.value.list.itemList.toMutableList()
        val item = playlistItems.removeAt(calculateDisplayIndex(fromIndex))
        playlistItems.add(calculateDisplayIndex(toIndex),item)
        setState { state ->
            state.copy(
                list = state.list.copy(itemList = playlistItems)
            )
        }
        updateDisplayItems()
    }

    suspend fun removeItem(streamInfo: StreamInfo) {
        setState { state ->
            val playlistItems = uiState.value.list.itemList.toMutableList().filter {
                when (uiState.value.playlistType) {
                    PlaylistType.LOCAL -> {
                        it.joinId != streamInfo.joinId
                    }
                    PlaylistType.HISTORY -> {
                        it.url != streamInfo.url
                    }
                    else -> true
                }
            }
            state.copy(
                list = state.list.copy(itemList = playlistItems)
            )
        }
        updateDisplayItems()
    }

    fun calculateDisplayIndex(originalIndex: Int): Int {
        val actualIndex = originalIndex - 1
        val state = uiState.value
        val totalItems = state.list.itemList.size
        return when (state.sortMode) {
            PlaylistSortMode.ORIGIN_REVERSE -> totalItems - 1 - actualIndex
            PlaylistSortMode.ORIGIN -> actualIndex
        }
    }
    fun StreamInfo.matchFilter(query: String): Boolean {
        val title = this.name?.lowercase() ?: ""
        val uploader = this.uploaderName?.lowercase() ?: ""
        val searchQuery = query.lowercase()
        return title.contains(searchQuery) || uploader.contains(searchQuery)
    }

    val sortedItems: List<StreamInfo> get() {
        return when (uiState.value.sortMode) {
            PlaylistSortMode.ORIGIN -> uiState.value.list.itemList
            PlaylistSortMode.ORIGIN_REVERSE -> uiState.value.list.itemList.reversed()
        }
    }

    private val sortedAndFilteredItems: List<StreamInfo> get() = sortedItems.filter { it.matchFilter(uiState.value.searchQuery) }
    fun updateDisplayItems() {
        setState { state ->
            state.copy(
                displayItems = sortedAndFilteredItems
            )
        }
    }

    fun updatePlaylistName(newName: String) {
        setState { currentState ->
            currentState.copy(
                playlistInfo = currentState.playlistInfo?.copy(name = newName)
            )
        }
    }

    suspend fun updateFeedLastUpdated(feedId: Long) {
        val lastUpdated = if (feedId != -1L) {
            DatabaseOperations.getEarliestLastUpdatedByFeedGroup(feedId)
        } else {
            DatabaseOperations.getEarliestLastUpdatedForAll()
        }
        setState { state ->
            state.copy(feedLastUpdated = lastUpdated)
        }
    }

    fun setRefreshing(isRefreshing: Boolean) {
        setState { state ->
            state.copy(isRefreshing = isRefreshing)
        }
    }
}