package project.pipepipe.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.FilterHelper
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.uistate.CommonUiState
import project.pipepipe.app.uistate.ErrorInfo
import project.pipepipe.app.uistate.ListUiState
import project.pipepipe.app.uistate.VideoDetailEntry
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.app.uistate.VideoDetailUiState
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.infoitem.RelatedItemInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.helper.executeJobFlow

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


    fun loadVideoDetails(url: String, serviceId: String? = null) {
        GlobalScope.launch {
            showAsDetailPage()
            SharedContext.updatePlaybackMode(PlaybackMode.AUDIO_ONLY)
            setDanmakuEnabled(SharedContext.settingsManager.getBoolean("danmaku_enabled", false))

            val currentEntry = uiState.value.currentEntry
            if (url == currentEntry?.streamInfo?.url) return@launch

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

            var resolvedServiceId = serviceId ?: DatabaseOperations.getStreamByUrl(url)?.service_id
            setState {
                it.copy(common = it.common.copy(isLoading = true, error = null))
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
                DatabaseOperations.updateOrInsertStreamHistory(newStreamInfo)

                if (newStreamInfo.commentInfo?.url != null) {
                    loadComments(newStreamInfo)
                }
                if (newStreamInfo.relatedItemInfo != null) {
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
                                ErrorInfo(fatalError.errorId!!, fatalError.code)
                            }
                        )
                    )
                }
            }
        }
    }


    fun navigateBack(): Boolean {
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
    }

    fun showAsDetailPage() {
        setState { it.copy(pageState = VideoDetailPageState.DETAIL_PAGE) }
    }

    fun hide() {
        setState { it.copy(pageState = VideoDetailPageState.HIDDEN) }
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        setState { it.copy(danmakuEnabled = enabled) }
    }

    fun toggleDanmaku() {
        SharedContext.settingsManager.putBoolean("danmaku_enabled", !uiState.value.danmakuEnabled)
        setState { it.copy(danmakuEnabled = !uiState.value.danmakuEnabled) }
    }

    suspend fun loadComments(streamInfo: StreamInfo) {
        val url = streamInfo.commentInfo?.url ?: return
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
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
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


    suspend fun loadMoreComments(serviceId: String) {
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
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
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


    suspend fun loadReplies(commentInfo: CommentInfo, serviceId: String) {
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
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
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

    suspend fun loadMoreReplies(serviceId: String) {
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
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
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
        val url = streamInfo.relatedItemInfo!!.url
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
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code)
                        )
                    )
                )
            }
            return
        }

        streamInfo.relatedItemInfo = result.info as RelatedItemInfo

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
                        itemList = filteredItems,
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
                            ErrorInfo(fatalError.errorId!!, fatalError.code)
                        }
                    ),
                    segments = result.pagedData?.itemList as List<SponsorBlockSegmentInfo>? ?: emptyList()
                )
            )
        }
    }

    suspend fun submitSponsorBlockSegment(url: String, segment: SponsorBlockSegmentInfo) {
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.SUBMIT_SPONSORBLOCK_SEGMENT,
                url,
                null,
                Json.encodeToString(segment)
            )
        }
        ToastManager.show("Success")
        loadSponsorBlock(url)
    }

    suspend fun voteSponsorBlockSegment(url: String, uuid: String, voteType: Int) {
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.VOTE_SPONSORBLOCK_SEGMENT,
                "$url&uuid=${uuid}&vote=$voteType",
                null,
            )
        }
        ToastManager.show("Success")
    }

    suspend fun loadDanmaku(url: String, serviceId: String) {
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
