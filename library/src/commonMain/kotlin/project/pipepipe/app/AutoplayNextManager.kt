package project.pipepipe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChangedBy
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.platform.PlatformMediaItem
import project.pipepipe.app.platform.toPlatformMediaItem
import project.pipepipe.shared.infoitem.RelatedItemInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.SupportedJobType

/**
 * AutoplayNextManager manages the automatic queuing of the next media item.
 *
 * This class:
 * - Monitors the current playback position
 * - Automatically loads and queues the next item when playing the last item
 * - Handles both partition-based and related-items-based next item selection
 * - Respects user settings for auto-queue behavior
 * - Prevents duplicate loading through deduplication
 */
class AutoplayNextManager {

    // ========== Internal State ==========

    /** Job for loading the next item */
    private var autoplayNextJob: Job? = null

    /** MediaId of the item being loaded (for deduplication) */
    private var autoplayNextMediaId: String? = null

    /** Last processed media item index (for detecting changes) */
    private var lastProcessedIndex: Int = -1

    /** Last processed media item ID (for detecting changes) */
    private var lastProcessedMediaId: String? = null

    init {
        // Monitor current media item changes
        GlobalScope.launch {
            SharedContext.queueManager.currentItem
                .filterNotNull()
                .distinctUntilChangedBy { it.mediaId to it.extras?.get("KEY_RELATED_ITEM_URL") }
                .collect { item ->
                    onMediaItemChanged(item)
                }
        }
    }

    // ========== Internal Logic ==========

    private fun onMediaItemChanged(item: PlatformMediaItem) {
        val queueSize = SharedContext.queueManager.mediaItemCount.value
        val currentIndex = SharedContext.queueManager.currentIndex.value

        // Check if auto-queue is enabled
        if (!SharedContext.settingsManager.getBoolean("auto_queue_key", false)) {
            return
        }

        // Only load for the last item
        if (currentIndex != queueSize - 1) {
            return
        }

        // Get related item URL and service ID from extras
        val relatedItemUrl = item.extras?.get("KEY_RELATED_ITEM_URL") as? String
        val serviceId = item.serviceId

        if (relatedItemUrl == null) {
            return
        }

        // Check if already processed this item
        if (lastProcessedMediaId == item.mediaId && lastProcessedIndex == currentIndex) {
            return
        }

        // Check if already loading for the same mediaId (deduplication)
        if (autoplayNextMediaId == item.mediaId && autoplayNextJob?.isActive == true) {
            return
        }

        // Update last processed state
        lastProcessedMediaId = item.mediaId
        lastProcessedIndex = currentIndex

        // Load the next item
        loadAutoplayNext(item.mediaId, relatedItemUrl, serviceId)
    }

    private fun loadAutoplayNext(mediaId: String, relatedItemUrl: String, serviceId: Int?) {
        // Cancel any existing job
        autoplayNextJob?.cancel()
        autoplayNextMediaId = mediaId

        autoplayNextJob = GlobalScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    executeJobFlow(
                        SupportedJobType.FETCH_INFO,
                        relatedItemUrl,
                        serviceId
                    )
                }
                val relatedItemInfo = result.info as? RelatedItemInfo
                val partitions = relatedItemInfo?.partitions

                // Check if we have partitions and can find next item
                if (!partitions.isNullOrEmpty()) {
                    // Find current partition index by mediaId (url)
                    val currentIndex = partitions.indexOfFirst { it.url == mediaId }
                    if (currentIndex != -1 && currentIndex < partitions.size - 1) {
                        // Get next partition item
                        val nextItem = partitions[currentIndex + 1]

                        if (SharedContext.queueManager.isLastIndex) {
                            MainScope().launch{ SharedContext.queueManager.addItem(nextItem.toPlatformMediaItem()) }
                        }
                        return@launch
                    }
                }

                // Fallback to relatedItems logic if no partition match
                val relatedItems = result.pagedData?.itemList as? List<StreamInfo> ?: return@launch
                if (relatedItems.isEmpty()) return@launch

                // Filter items based on duration setting
                val filterLongVideos = SharedContext.settingsManager.getBoolean("dont_auto_queue_long_key", true)
                val filteredItems = relatedItems.filter {
                    if (filterLongVideos) {
                        runCatching { it.duration!! <= 360 }.getOrDefault(true)
                    } else true
                }

                if (filteredItems.isEmpty()) return@launch

                // Pick a random related item
                val randomItem = filteredItems.random()

                if (SharedContext.queueManager.isLastIndex) {
                    MainScope().launch{ SharedContext.queueManager.addItem(randomItem.toPlatformMediaItem()) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Reset loading state
                autoplayNextMediaId = null
            }
        }
    }
}
