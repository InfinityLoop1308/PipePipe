package project.pipepipe.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.FilterHelper
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Listen for playlist changes
        SharedContext.playlistChanged
            .onEach { changedPlaylistId ->
                val currentPlaylistId = uiState.value.playlistInfo?.uid
                val currentUrl = uiState.value.playlistInfo?.url

                // Reload if this is a local playlist and it matches the changed playlist
                if (currentPlaylistId == changedPlaylistId &&
                    currentUrl != null &&
                    uiState.value.playlistType == PlaylistType.LOCAL) {
                    loadPlaylist(currentUrl, uiState.value.playlistInfo?.serviceId)
                }
            }
            .launchIn(scope)

        // Listen for history changes
        SharedContext.historyChanged
            .onEach {
                val currentUrl = uiState.value.playlistInfo?.url

                // Reload if this is the history playlist
                if (currentUrl == "local://history" &&
                    uiState.value.playlistType == PlaylistType.HISTORY) {
                    loadPlaylist(currentUrl, uiState.value.playlistInfo?.serviceId)
                }
            }
            .launchIn(scope)
    }

    fun onCleared() {
        scope.cancel()
    }

    suspend fun loadPlaylist(url: String, serviceId: String? = null) {
        setState {
            it.copy(
                common = it.common.copy(isLoading = true, error = null)
            )
        }
        val sortMode = runCatching{ PlaylistSortMode.valueOf(SharedContext.settingsManager.getString("playlist_sort_mode_key")) }
            .getOrDefault(PlaylistSortMode.ORIGIN)
        when {
            url.startsWith("local://playlist") -> {
                val playlistId = url.substringAfterLast("/")
                val playlistInfo = DatabaseOperations.getPlaylistInfoById(playlistId)
                val streamInfos = DatabaseOperations.loadPlaylistsItemsFromDatabase(playlistId.toLong())
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
                val rawStreamInfos = DatabaseOperations.getFeedStreamsByGroup(feedId)

                // Apply filters
                val (filteredItems, _) = FilterHelper.filterStreamInfoList(
                    rawStreamInfos,
                    FilterHelper.FilterScope.CHANNELS
                )

                // Mark new items if this is a refresh
                val previousUrls = uiState.value.previousItemUrls
                val itemsWithNewFlag = if (previousUrls.isNotEmpty()) {
                    filteredItems.map { item ->
                        if (item.url !in previousUrls) {
                            item.copy(isNew = true)
                        } else {
                            item
                        }
                    }
                } else {
                    filteredItems
                }

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
                        list = state.list.copy(itemList = itemsWithNewFlag),
                        displayItems = itemsWithNewFlag,
                        previousItemUrls = itemsWithNewFlag.map { it.url }.toSet()
                    )
                }
                updateFeedLastUpdated(feedId)
            }
            url.startsWith("trending://") -> {
                loadRemotePlaylistDetail(url, serviceId!!, isTrending = true)
            }
            else -> {
                loadRemotePlaylistDetail(url, serviceId!!, isTrending = false)
            }
        }
    }

    suspend fun loadRemotePlaylistDetail(url: String, serviceId: String, isTrending: Boolean = false) {
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
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, serviceId)
                    )
                )
            }
            return
        }

        // Check if this remote playlist is bookmarked in database
        val playlistInfo = result.info as? PlaylistInfo
        val bookmarkedPlaylist = if (playlistInfo?.serviceId != null) {
            DatabaseOperations.getRemotePlaylistByUrl(playlistInfo.url)
        } else null

        val finalPlaylistInfo = if (bookmarkedPlaylist != null) {
            playlistInfo?.copy(
                uid = bookmarkedPlaylist.uid,
                isPinned = bookmarkedPlaylist.is_pinned != 0L
            )
        } else {
            playlistInfo
        }

        val rawItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
            .distinctBy { it.url }

        // Apply filters only for trending
        val items = if (isTrending) {
            val (filteredItems, _) = FilterHelper.filterStreamInfoList(
                rawItems,
                FilterHelper.FilterScope.RECOMMENDATIONS
            )
            filteredItems
        } else {
            rawItems
        }

        val sortMode = runCatching{ PlaylistSortMode.valueOf(SharedContext.settingsManager.getString("playlist_sort_mode_key")) }
            .getOrDefault(PlaylistSortMode.ORIGIN)
        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                playlistInfo = finalPlaylistInfo,
                playlistType = if (isTrending) PlaylistType.TRENDING else PlaylistType.REMOTE,
                list = ListUiState(
                    itemList = items,
                    nextPageUrl = result.pagedData?.nextPageUrl
                ),
                sortMode = sortMode
            )
        }
        setState {
            it.copy(displayItems = sortedItems)
        }
    }

    suspend fun loadRemotePlaylistMoreItems(serviceId: String) {
        val nextUrl = uiState.value.list.nextPageUrl ?: return
        setState {
            it.copy(
                common = it.common.copy(isLoading = true, error = null)
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
                        error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, serviceId)
                    )
                )
            }
            return
        }


        setState {
            val newItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
            val combinedList = (it.list.itemList + newItems)
                .distinctBy { item -> item.url }

            // Apply filters only for trending
            val finalList = if (it.playlistType == PlaylistType.TRENDING) {
                val (filteredList, _) = FilterHelper.filterStreamInfoList(
                    combinedList,
                    FilterHelper.FilterScope.RECOMMENDATIONS
                )
                filteredList
            } else {
                combinedList
            }

            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                list = it.list.copy(
                    itemList = finalList,
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
        updateDisplayItems()

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
        val playlistId = uiState.value.playlistInfo?.uid
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

        // Notify playlist changed for local playlists
        if (uiState.value.playlistType == PlaylistType.LOCAL && playlistId != null) {
            SharedContext.notifyPlaylistChanged(playlistId)
        }
    }

    fun calculateDisplayIndex(originalIndex: Int): Int {
        val actualIndex = originalIndex - 1
        val state = uiState.value
        val totalItems = state.list.itemList.size
        return when (state.sortMode) {
            PlaylistSortMode.ORIGIN_REVERSE -> totalItems - 1 - actualIndex
            else -> actualIndex
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
            PlaylistSortMode.UPLOAD_TIME_ASCENDING -> uiState.value.list.itemList.sortedBy { it.uploadDate ?: Long.MAX_VALUE }
            PlaylistSortMode.UPLOAD_TIME_DESCENDING -> uiState.value.list.itemList.sortedByDescending { it.uploadDate ?: Long.MIN_VALUE }
            PlaylistSortMode.DURATION_ASCENDING -> uiState.value.list.itemList.sortedBy { it.duration ?: Long.MAX_VALUE }
            PlaylistSortMode.DURATION_DESCENDING -> uiState.value.list.itemList.sortedByDescending { it.duration ?: Long.MIN_VALUE }
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