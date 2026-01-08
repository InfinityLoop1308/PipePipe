package project.pipepipe.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.FilterHelper
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.platform.PlaybackState
import project.pipepipe.app.uistate.*
import project.pipepipe.shared.infoitem.*
import project.pipepipe.shared.job.SupportedJobType

class VideoDetailViewModel()
    : BaseViewModel<VideoDetailUiState>(VideoDetailUiState()) {

    private inline fun updateCurrentEntry(
        crossinline transform: (VideoDetailEntry) -> VideoDetailEntry
    ) {
        setState { state ->
            if (state.streamInfoStack.isEmpty()) return@setState state
            val newStack = state.streamInfoStack.toMutableList()
            val lastIndex = newStack.lastIndex
            newStack[lastIndex] = transform(newStack[lastIndex])
            state.copy(streamInfoStack = newStack)
        }
    }


    fun loadVideoDetails(url: String, serviceId: Int? = null,
                         shouldDisableLoading: Boolean = false,
                         shouldKeepPlaybackMode: Boolean = false,
                         shouldNotChangePageState: Boolean = false) {
        GlobalScope.launch {
            if (!shouldNotChangePageState) {
                showAsDetailPage()
            }
            val currentEntry = uiState.value.currentEntry
            if (url == currentEntry?.streamInfo?.url) return@launch
            setDanmakuEnabled(SharedContext.settingsManager.getBoolean("danmaku_enabled", false))
            if (!shouldKeepPlaybackMode) {
                SharedContext.updatePlaybackMode(PlaybackMode.AUDIO_ONLY)
            }

            val existingIndex = uiState.value.streamInfoStack.indexOfFirst { it.streamInfo.url == url }
            if (existingIndex != -1) {
                val entry = uiState.value.streamInfoStack[existingIndex]
                setState { state ->
                    val newStack = state.streamInfoStack.toMutableList().apply {
                        removeAt(existingIndex)
                        add(entry)
                    }
                    state.copy(
                        streamInfoStack = newStack,
                        common = state.common.copy(isLoading = false, error = null)
                    )
                }
                return@launch
            }

            val resolvedServiceId = serviceId ?: DatabaseOperations.getStreamByUrl(url)?.service_id?.toInt()
            if (!shouldDisableLoading) {
                setState {
                    it.copy(common = it.common.copy(isLoading = true, error = null))
                }
            }


            val result = withContext(Dispatchers.IO) {
                executeJobFlow(SupportedJobType.FETCH_INFO, url, resolvedServiceId)
            }


            if (result.fatalError == null) {
                val newStreamInfo = (result.info as? StreamInfo)!!
                val newEntry = VideoDetailEntry(newStreamInfo)
                setState {
                    it.copy(
                        common = it.common.copy(
                            isLoading = false
                        ),
                        streamInfoStack = it.streamInfoStack + newEntry
                    )
                }
                val watchHistoryMode = SharedContext.settingsManager.getString("watch_history_mode", "on_play")
                if (watchHistoryMode == "on_click") {
                    DatabaseOperations.updateOrInsertStreamHistory(newStreamInfo)
                }

                if (newStreamInfo.commentUrl != null) {
                    loadComments(newStreamInfo)
                }
                if (newStreamInfo.relatedItemUrl != null) {
                    loadRelatedItems(newStreamInfo)
                }
                if (newStreamInfo.sponsorblockUrl != null) {
                    loadSponsorBlock(newStreamInfo.sponsorblockUrl!!)
                }
                if (newStreamInfo.danmakuUrl != null) {
                    loadDanmaku(newStreamInfo.danmakuUrl!!, newStreamInfo.serviceId)
                }
            } else {
                setState {
                    it.copy(
                        common = it.common.copy(
                            isLoading = false,
                            error = result.fatalError!!.let { fatalError ->
                                ErrorInfo(fatalError.errorId!!, fatalError.code, resolvedServiceId!!)
                            }
                        )
                    )
                }
            }
        }
    }


    fun navigateBack(): Boolean {
        if (uiState.value.common.error != null && uiState.value.streamInfoStack.isNotEmpty()) {
            setState {
                it.copy(common = CommonUiState())
            }
            return true
        }


        SharedContext.updatePlaybackMode(PlaybackMode.AUDIO_ONLY)
        if (!uiState.value.canNavigateBack) {
            return false
        }

        setState {
            it.copy(
                common = CommonUiState(),
                streamInfoStack = it.streamInfoStack.dropLast(1)
            )
        }
        return true
    }

    fun setPageState(pageState: VideoDetailPageState) {
        setState { it.copy(pageState = pageState) }
    }

    fun toggleFullscreenPlayer() {
        setState {
            it.copy(
                pageState = if (it.pageState == VideoDetailPageState.FULLSCREEN_PLAYER) {
                    VideoDetailPageState.DETAIL_PAGE
                } else {
                    VideoDetailPageState.FULLSCREEN_PLAYER
                }
            )
        }
    }

    fun showAsBottomPlayer() {
        setState { it.copy(pageState = VideoDetailPageState.BOTTOM_PLAYER) }
        val controller = SharedContext.platformMediaController
        val streamInfo = uiState.value.currentStreamInfo
        if (SharedContext.playbackMode.value == PlaybackMode.VIDEO_AUDIO
            && SharedContext.queueManager.currentItem.value?.mediaId == streamInfo?.url) {
            SharedContext.playingVideoUrlBeforeMinimizing = streamInfo?.url
        }
        SharedContext.updatePlaybackMode(PlaybackMode.AUDIO_ONLY)
        if (streamInfo != null && controller != null &&
            (SharedContext.queueManager.mediaItemCount.value == 0 ||
                    (SharedContext.queueManager.mediaItemCount.value in listOf(1,2) && controller.playbackState.value == PlaybackState.IDLE))) {
            controller.setStreamInfoAsOnlyMediaItem(streamInfo)
        }
    }

    fun showAsDetailPage() {
        setState { it.copy(pageState = VideoDetailPageState.DETAIL_PAGE) }
    }

    fun hide() {
        setState { it.copy(pageState = VideoDetailPageState.HIDDEN, streamInfoStack = emptyList())}
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        setState { it.copy(danmakuEnabled = enabled) }
    }

    fun toggleDanmaku() {
        SharedContext.settingsManager.putBoolean("danmaku_enabled", !uiState.value.danmakuEnabled)
        setState { it.copy(danmakuEnabled = !uiState.value.danmakuEnabled) }
    }

    suspend fun loadComments(streamInfo: StreamInfo) {
        val url = streamInfo.commentUrl ?: return
        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    common = entry.cachedComments.common.copy(isLoading = true),
                    comments = ListUiState()
                )
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(SupportedJobType.FETCH_FIRST_PAGE, url, streamInfo.serviceId)
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            updateCurrentEntry { entry ->
                entry.copy(
                    cachedComments = entry.cachedComments.copy(
                        common = entry.cachedComments.common.copy(
                            isLoading = false,
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, streamInfo.serviceId)
                        )
                    )
                )
            }
            return
        }

        updateCurrentEntry { entry ->
            val current = entry.cachedComments
            entry.copy(
                cachedComments = current.copy(
                    common = current.common.copy(
                        isLoading = false,
                        error = null
                    ),
                    comments = current.comments.copy(
                        itemList = (result.pagedData?.itemList as? List<CommentInfo>).orEmpty(),
                        nextPageUrl = result.pagedData?.nextPageUrl,
                        firstVisibleItemIndex = 0,
                        firstVisibleItemScrollOffset = 0
                    )
                )
            )
        }
    }


    suspend fun loadMoreComments(serviceId: Int) {
        val nextUrl = uiState.value.currentComments.comments.nextPageUrl ?: return
        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    common = entry.cachedComments.common.copy(isLoading = true)
                )
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(SupportedJobType.FETCH_GIVEN_PAGE, nextUrl, serviceId)
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            updateCurrentEntry { entry ->
                entry.copy(
                    cachedComments = entry.cachedComments.copy(
                        common = entry.cachedComments.common.copy(
                            isLoading = false,
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, serviceId)
                        )
                    )
                )
            }
            return
        }

        updateCurrentEntry { entry ->
            val current = entry.cachedComments
            entry.copy(
                cachedComments = current.copy(
                    common = current.common.copy(
                        isLoading = false,
                        error = null
                    ),
                    comments = current.comments.copy(
                        itemList = current.comments.itemList + (result.pagedData?.itemList as? List<CommentInfo>).orEmpty(),
                        nextPageUrl = result.pagedData?.nextPageUrl,
                        firstVisibleItemIndex = current.comments.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = current.comments.firstVisibleItemScrollOffset
                    )
                )
            )
        }
    }


    suspend fun loadReplies(commentInfo: CommentInfo, serviceId: Int) {
        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    common = entry.cachedComments.common.copy(isLoading = true),
                    parentComment = commentInfo
                )
            )
        }
        val url = commentInfo.replyInfo!!.url!!
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_FIRST_PAGE,
                url,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            updateCurrentEntry { entry ->
                entry.copy(
                    cachedComments = entry.cachedComments.copy(
                        common = entry.cachedComments.common.copy(
                            isLoading = false,
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, serviceId)
                        )
                    )
                )
            }
            return
        }

        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    common = entry.cachedComments.common.copy(
                        isLoading = false,
                        error = null
                    ),
                    replies = entry.cachedComments.replies.copy(
                        itemList = (result.pagedData?.itemList as? List<CommentInfo>).orEmpty(),
                        nextPageUrl = result.pagedData?.nextPageUrl,
                        firstVisibleItemIndex = 0,
                        firstVisibleItemScrollOffset = 0
                    )
                )
            )
        }
    }

    suspend fun loadMoreReplies(serviceId: Int) {
        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    common = entry.cachedComments.common.copy(isLoading = true),
                )
            )
        }
        val nextUrl = uiState.value.currentComments.replies.nextPageUrl!!
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_GIVEN_PAGE,
                nextUrl,
                serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            updateCurrentEntry { entry ->
                entry.copy(
                    cachedComments = entry.cachedComments.copy(
                        common = entry.cachedComments.common.copy(
                            isLoading = false,
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, serviceId)
                        )
                    )
                )
            }
            return
        }

        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    common = entry.cachedComments.common.copy(
                        isLoading = false,
                        error = null
                    ),
                    replies = entry.cachedComments.replies.copy(
                        itemList = entry.cachedComments.replies.itemList + ((result.pagedData?.itemList as? List<CommentInfo>).orEmpty()),
                        nextPageUrl = result.pagedData?.nextPageUrl,
                        firstVisibleItemIndex = entry.cachedComments.replies.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = entry.cachedComments.replies.firstVisibleItemScrollOffset
                    )
                )
            )
        }
    }

    fun backToCommentList() {
        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    parentComment = null,
                    replies = ListUiState()
                )
            )
        }
    }

    suspend fun loadRelatedItems(streamInfo: StreamInfo) {
        val url = streamInfo.relatedItemUrl
        updateCurrentEntry { entry ->
            entry.copy(
                cachedRelatedItems = entry.cachedRelatedItems.copy(
                    common = entry.cachedRelatedItems.common.copy(isLoading = true),
                )
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_INFO,
                url,
                streamInfo.serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            updateCurrentEntry { entry ->
                entry.copy(
                    cachedRelatedItems = entry.cachedRelatedItems.copy(
                        common = entry.cachedRelatedItems.common.copy(
                            isLoading = false,
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, streamInfo.serviceId)
                        )
                    )
                )
            }
            return
        }


        // Apply filters
        val rawItems = (result.pagedData?.itemList as? List<StreamInfo>).orEmpty()
        val (filteredItems, _) = FilterHelper.filterStreamInfoList(
            rawItems,
            FilterHelper.FilterScope.RELATED_ITEM
        )

        updateCurrentEntry { entry ->
            entry.copy(
                cachedRelatedItems = entry.cachedRelatedItems.copy(
                    common = entry.cachedRelatedItems.common.copy(
                        isLoading = false,
                        error = null
                    ),
                    list = entry.cachedRelatedItems.list.copy(
                        itemList = (result.info as? RelatedItemInfo)?.partitions?: filteredItems,
                        nextPageUrl = result.pagedData?.nextPageUrl,
                        firstVisibleItemIndex = 0,
                        firstVisibleItemScrollOffset = 0
                    )
                )
            )
        }
    }
    suspend fun loadSponsorBlock(url: String) {
        updateCurrentEntry { entry ->
            entry.copy(
                cachedSponsorBlock = entry.cachedSponsorBlock.copy(
                    common = entry.cachedSponsorBlock.common.copy(isLoading = true)
                )
            )
        }

        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_SPONSORBLOCK_SEGMENT_LIST,
                url,
                null
            )
        }

        updateCurrentEntry { entry ->
            entry.copy(
                cachedSponsorBlock = entry.cachedSponsorBlock.copy(
                    common = entry.cachedSponsorBlock.common.copy(
                        isLoading = false,
                        error = result.fatalError?.let { fatalError ->
                            ErrorInfo(fatalError.errorId!!, fatalError.code, -1)
                        }
                    ),
                    segments = result.pagedData?.itemList as List<SponsorBlockSegmentInfo>? ?: emptyList()
                )
            )
        }
    }

    suspend fun submitSponsorBlockSegment(url: String, segment: SponsorBlockSegmentInfo, msg: String) {
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.SUBMIT_SPONSORBLOCK_SEGMENT,
                url,
                null,
                Json.encodeToString(segment)
            )
        }
        ToastManager.show(msg)
        loadSponsorBlock(url)
    }

    suspend fun voteSponsorBlockSegment(url: String, uuid: String, voteType: Int, msg: String) {
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.VOTE_SPONSORBLOCK_SEGMENT,
                "$url&uuid=${uuid}&vote=$voteType",
                null,
            )
        }
        ToastManager.show(msg)
    }

    suspend fun loadDanmaku(url: String, serviceId: Int) {
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_FIRST_PAGE,
                url,
                serviceId
            )
        }
        updateCurrentEntry { entry ->
            entry.copy(
                cachedDanmaku = entry.cachedDanmaku + (result.pagedData!!.itemList as List<DanmakuInfo>)
            )
        }
    }

    fun updateCommentsScrollPosition(position: Int, offset: Int) {
        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    comments = entry.cachedComments.comments.copy(
                        firstVisibleItemIndex = position,
                        firstVisibleItemScrollOffset = offset
                    )
                )
            )
        }
    }


    fun updateRepliesScrollPosition(position: Int, offset: Int) {
        updateCurrentEntry { entry ->
            entry.copy(
                cachedComments = entry.cachedComments.copy(
                    replies = entry.cachedComments.replies.copy(
                        firstVisibleItemIndex = position,
                        firstVisibleItemScrollOffset = offset
                    )
                )
            )
        }
    }

    fun updateRelatedItemsScrollPosition(position: Int, offset: Int) {
        updateCurrentEntry { entry ->
            entry.copy(
                cachedRelatedItems = entry.cachedRelatedItems.copy(
                    list = entry.cachedRelatedItems.list.copy(
                        firstVisibleItemIndex = position,
                        firstVisibleItemScrollOffset = offset
                    )
                )
            )
        }
    }
}
