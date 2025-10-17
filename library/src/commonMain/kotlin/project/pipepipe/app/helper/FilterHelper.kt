package project.pipepipe.app.helper

import project.pipepipe.app.SharedContext
import project.pipepipe.shared.infoitem.StreamInfo

object FilterHelper {

    enum class FilterScope {
        SEARCH_RESULT,
        RECOMMENDATIONS,
        RELATED_ITEM,
        CHANNELS
    }

    /**
     * Filters a list of StreamInfo items based on user settings
     * @param items The list of items to filter
     * @param scope The scope where this filter is applied
     * @return Pair of filtered list and count of filtered items
     */
    fun filterStreamInfoList(
        items: List<StreamInfo>,
        scope: FilterScope
    ): Pair<List<StreamInfo>, Int> {
        // Check if filtering is enabled for this scope
        val filterTypes = SharedContext.settingsManager.getStringSet(
            "filter_type_key",
            setOf("search_result", "recommendations", "related_item", "channels")
        )

        val scopeKey = when (scope) {
            FilterScope.SEARCH_RESULT -> "search_result"
            FilterScope.RECOMMENDATIONS -> "recommendations"
            FilterScope.RELATED_ITEM -> "related_item"
            FilterScope.CHANNELS -> "channels"
        }

        if (!filterTypes.contains(scopeKey)) {
            return Pair(items, 0)
        }

        // Get filter settings
        val filterKeywords = SharedContext.settingsManager.getStringSet(
            "filter_by_keyword_key_set",
            emptySet()
        )

        val filterChannels = SharedContext.settingsManager.getStringSet(
            "filter_by_channel_key_set",
            emptySet()
        )

        val filterShorts = SharedContext.settingsManager.getBoolean(
            "filter_shorts_key",
            false
        )

        val filterPaid = SharedContext.settingsManager.getBoolean(
            "filter_paid_contents_key",
            false
        )

        // Apply filters
        val filteredItems = items.filter { item ->
            // Filter by keywords
            if (filterKeywords.isNotEmpty()) {
                val title = item.name?.lowercase() ?: ""
                val matchesKeyword = filterKeywords.any { keyword ->
                    title.contains(keyword.lowercase())
                }
                if (matchesKeyword) return@filter false
            }

            // Filter by channel name
            if (filterChannels.isNotEmpty()) {
                val uploaderName = item.uploaderName?.lowercase() ?: ""
                val matchesChannel = filterChannels.any { channel ->
                    uploaderName.contains(channel.lowercase())
                }
                if (matchesChannel) return@filter false
            }

            // Filter shorts
            if (filterShorts && item.isShort) {
                return@filter false
            }

            // Filter paid content
            if (filterPaid && item.isPaid) {
                return@filter false
            }

            true
        }

        val filteredCount = items.size - filteredItems.size

        // Show toast if more than 1 item was filtered
        if (filteredCount > 1) {
            ToastManager.show("$filteredCount items filtered")
        }

        return Pair(filteredItems, filteredCount)
    }
}
