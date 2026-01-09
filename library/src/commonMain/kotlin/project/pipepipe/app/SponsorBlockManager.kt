package project.pipepipe.app

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import project.pipepipe.app.helper.SponsorBlockHelper
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.job.SupportedJobType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import project.pipepipe.app.platform.PlatformMediaItem

/**
 * SponsorBlockManager manages SponsorBlock segment detection and skipping logic.
 *
 * This class:
 * - Loads SponsorBlock segments for current media item
 * - Detects which segment is currently playing
 * - Automatically skips segments based on settings
 * - Provides manual skip/unskip controls for UI
 * - Exposes state as StateFlow for reactive UI
 *
 * SponsorBlockManager is single source of truth for:
 * - Current segment being played
 * - Skip button visibility state
 * - Unskip button visibility state
 * - Which segments have been skipped
 */
class SponsorBlockManager(private val sponsorblockNames: List<String>, private val sponsorBlockSkipMsg: String) {
    val platformMediaController = SharedContext.platformMediaController!!
    // ========== Public StateFlow (exposed to UI) ==========

    /** Current SponsorBlock segment at playback position */
    private val _currentSegment = MutableStateFlow<SponsorBlockSegmentInfo?>(null)
    val currentSegment: StateFlow<SponsorBlockSegmentInfo?> = _currentSegment.asStateFlow()

    /** All segments for the current media item */
    private val _currentSegments = MutableStateFlow<List<SponsorBlockSegmentInfo>>(emptyList())
    val currentSegments: StateFlow<List<SponsorBlockSegmentInfo>> = _currentSegments.asStateFlow()

    /** Whether to show the skip button (for MANUAL mode segments) */
    private val _showSkipButton = MutableStateFlow(false)
    val showSkipButton: StateFlow<Boolean> = _showSkipButton.asStateFlow()

    /** Whether to show the unskip button */
    private val _showUnskipButton = MutableStateFlow(false)
    val showUnskipButton: StateFlow<Boolean> = _showUnskipButton.asStateFlow()

    // ========== Internal State ==========

    /** Cache of segments for each mediaId */
    private val segmentsCache = mutableMapOf<String, List<SponsorBlockSegmentInfo>>()

    /** Set of segment UUIDs that have been skipped, per mediaId */
    private val skippedSegments = mutableMapOf<String, MutableSet<String>>()

    /** Currently playing mediaId */
    private var currentMediaId: String? = null

    /** Last skipped segment for unskip functionality */
    private var lastSkippedSegment: SponsorBlockSegmentInfo? = null

    private val shownSkipSegments = mutableMapOf<String, MutableSet<String>>()

    // 新增：用于超时隐藏的Job
    private var skipButtonTimeoutJob: Job? = null
    private var unskipButtonTimeoutJob: Job? = null


    init {
        // Monitor media item changes (including extras updates like SponsorBlock URL)
        GlobalScope.launch {
            SharedContext.queueManager.currentItem
                .filterNotNull()
                .distinctUntilChangedBy { it.mediaId to it.extras?.get("KEY_SPONSORBLOCK_URL") }
                .collect { item ->
                    onMediaItemChanged(item)
                }
        }

        // Monitor position changes
        GlobalScope.launch {
            platformMediaController.currentPosition.collect { position ->
                onPositionChanged(position)
            }
        }
    }

    // ========== Internal Logic ==========

    private suspend fun onMediaItemChanged(item: PlatformMediaItem) {
        if (!SponsorBlockHelper.isEnabled()) {
            currentMediaId = null
            return
        }
        currentMediaId = item.mediaId

        // Reset skipped segments for this media
        skippedSegments.getOrPut(item.mediaId) { mutableSetOf() }.clear()
        shownSkipSegments.getOrPut(item.mediaId) { mutableSetOf() }.clear()

        // Load segments if not cached
        val sponsorBlockUrl = item.extras?.get("KEY_SPONSORBLOCK_URL") as? String
        if (sponsorBlockUrl != null && !segmentsCache.containsKey(item.mediaId)) {
            loadSegments(item.mediaId, sponsorBlockUrl)
        } else if (segmentsCache.containsKey(item.mediaId)) {
            // Already cached, just update the segments flow
            _currentSegments.value = segmentsCache[item.mediaId] ?: emptyList()
        }

        // Reset UI state
        _currentSegment.value = null
        _showSkipButton.value = false
        _showUnskipButton.value = false
        lastSkippedSegment = null
    }

    private fun onPositionChanged(position: Long) {
        if (!SponsorBlockHelper.isEnabled() || !SponsorBlockHelper.isSkipEnabled()) {
            _showSkipButton.value = false
            _currentSegment.value = null
            return
        }

        val mediaId = currentMediaId ?: return
        val segments = segmentsCache[mediaId] ?: return

        // Find segment at current position
        val segment = segments.firstOrNull { segment ->
            position.toDouble() in segment.startTime..segment.endTime
        }

        _currentSegment.value = segment

        if (segment == null) {
            return
        }

        val alreadySkipped = skippedSegments[mediaId]?.contains(segment.uuid) == true
        val alreadyShown = shownSkipSegments[mediaId]?.contains(segment.uuid) == true


        // Auto-skip logic
        if (SponsorBlockHelper.isSkipEnabled() &&
            SponsorBlockHelper.shouldSkipSegment(segment) &&
            !alreadySkipped) {
            skipSegment(segment)
            return
        }

        // Manual skip button logic
        if (!alreadySkipped && !alreadyShown && SponsorBlockHelper.shouldShowSkipButton(segment)) {
            showSkipButtonWithTimeout(mediaId, segment)
        }
    }

    private fun showSkipButtonWithTimeout(mediaId: String, segment: SponsorBlockSegmentInfo) {
        // 记录已显示
        shownSkipSegments.getOrPut(mediaId) { mutableSetOf() }.add(segment.uuid)

        _showSkipButton.value = true

        // 取消之前的timeout
        skipButtonTimeoutJob?.cancel()
        skipButtonTimeoutJob = GlobalScope.launch {
            delay(5000)
            _showSkipButton.value = false
        }
    }

    private suspend fun loadSegments(mediaId: String, sponsorBlockUrl: String) {
        try {
            val result = withContext(Dispatchers.IO) {
                executeJobFlow(
                    SupportedJobType.FETCH_SPONSORBLOCK_SEGMENT_LIST,
                    sponsorBlockUrl,
                    null
                )
            }

            val segments = result.pagedData?.itemList as? List<SponsorBlockSegmentInfo> ?: emptyList()
            segmentsCache[mediaId] = segments

            // Update segments flow if this is the current media
            if (currentMediaId == mediaId) {
                _currentSegments.value = segments
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ========== Public Methods ==========

    /**
     * Skip the specified segment.
     * @param segment The segment to skip
     */
    fun skipSegment(segment: SponsorBlockSegmentInfo, isManual: Boolean = false) {
        val mediaId = currentMediaId ?: return

        if (skippedSegments[mediaId]?.contains(segment.uuid) == true) {
            return
        }

        // Record as skipped
        skippedSegments.getOrPut(mediaId) { mutableSetOf() }.add(segment.uuid)

        // Seek to end of segment
        MainScope().launch{ platformMediaController.seekTo(segment.endTime.toLong()) }

        // Update state
        lastSkippedSegment = segment
        _showSkipButton.value = false

        // Show notification
        if (SponsorBlockHelper.isNotificationsEnabled()) {
            val categoryName = getCategoryDisplayName(segment)
            ToastManager.show(sponsorBlockSkipMsg.format(categoryName))
        }

        if (!isManual) {
            unskipButtonTimeoutJob?.cancel()
            _showUnskipButton.value = false
            return
        }

        _showUnskipButton.value = true

        // Unskip 5秒超时
        unskipButtonTimeoutJob?.cancel()
        unskipButtonTimeoutJob = GlobalScope.launch {
            delay(5000)
            _showUnskipButton.value = false
            lastSkippedSegment = null
        }
    }

    /**
     * Undo the last skip operation, returning to the segment's start.
     */
    fun unskipLastSegment() {
        val segment = lastSkippedSegment ?: return
        val mediaId = currentMediaId ?: return

        // Remove from skipped set
        skippedSegments[mediaId]?.remove(segment.uuid)

        // Seek back to start of segment
        MainScope().launch{ platformMediaController.seekTo(segment.startTime.toLong()) }

        // Update state
        lastSkippedSegment = null
        _showUnskipButton.value = false
    }

    /**
     * Get the display name for a segment's category.
     * @param segment The segment to get the category name for
     * @return The display name, or a fallback if not cached
     */
    fun getCategoryDisplayName(segment: SponsorBlockSegmentInfo): String {
        val categoryOrdinal = segment.category.ordinal
        return if (categoryOrdinal in sponsorblockNames.indices) {
            sponsorblockNames[categoryOrdinal]
        } else {
            segment.category.apiName
        }

    }
}
