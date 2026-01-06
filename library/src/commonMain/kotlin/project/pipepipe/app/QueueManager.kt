package project.pipepipe.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import project.pipepipe.app.platform.PlatformMediaItem
import project.pipepipe.app.platform.RepeatMode

/**
 * QueueManager manages play queue state independently of the underlying media player.
 *
 * This class:
 * - Maintains the current queue order and playback index
 * - Handles shuffle/unshuffle operations
 * - Exposes queue state as StateFlow
 * - Preserves the original queue for unshuffle functionality
 *
 * The implementation follows NewPipe's PlayQueue pattern where backup and queue
 * are always kept in sync through queue operations.
 *
 * QueueManager is the single source of truth for:
 * - Queue order (what items to play)
 * - Current playback position in the queue
 * - Shuffle state
 */
class QueueManager {
    private val _queue = MutableStateFlow<List<PlatformMediaItem>>(emptyList())
    val queue: StateFlow<List<PlatformMediaItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    val isLastIndex: Boolean get() = currentIndex.value == queue.value.size - 1

    private var backup: MutableList<PlatformMediaItem>? = null

    /**
     * Get current queue order.
     */
    fun getCurrentQueue(): List<PlatformMediaItem> = _queue.value

    /**
     * Get current playback index in the queue.
     */
    fun getCurrentIndex(): Int = _currentIndex.value

    /**
     * Get current item to be played, or null if queue is empty.
     */
    fun getCurrentItem(): PlatformMediaItem? = getCurrentQueue().getOrNull(_currentIndex.value)

    /**
     * Get the index of a specific item in the current queue.
     * @param mediaId The media ID to search for
     * @return The index of the item, or -1 if not found
     */
    fun getIndexOfItem(mediaId: String): Int {
        val currentQueue = _queue.value
        return currentQueue.indexOfFirst { it.mediaId == mediaId }
    }

    /**
     * Get next item in the queue, respecting repeat modes.
     */
    fun getNextItem(repeatMode: RepeatMode): PlatformMediaItem? {
        val currentQueue = _queue.value
        val index = _currentIndex.value

        return when (repeatMode) {
            RepeatMode.ONE -> getCurrentItem() // Repeat current
            RepeatMode.ALL -> {
                if (currentQueue.isEmpty()) null
                else currentQueue[(index + 1) % currentQueue.size]
            }
            RepeatMode.OFF -> {
                if (index + 1 < currentQueue.size) currentQueue[index + 1]
                else null
            }
        }
    }

    /**
     * Get previous item in the queue.
     */
    fun getPreviousItem(): PlatformMediaItem? {
        val currentQueue = _queue.value
        val index = _currentIndex.value

        return if (index > 0) currentQueue[index - 1] else null
    }

    /**
     * Check if queue is currently shuffled.
     * @return true if a backup queue exists (indicating shuffle mode)
     */
    fun isShuffled(): Boolean = backup != null

    /**
     * Set entire queue.
     * @param items New queue items
     * @param startIndex Index to start playing from
     */
    fun setQueue(items: List<PlatformMediaItem>, startIndex: Int = 0) {
        _queue.value = items.toList()
        _currentIndex.value = startIndex.coerceIn(0, items.size - 1)
        backup = null
    }

    /**
     * Set queue silently without emitting events (for initialization).
     */
    fun setQueueSilent(items: List<PlatformMediaItem>, startIndex: Int = 0) {
        _queue.value = items.toList()
        _currentIndex.value = startIndex.coerceIn(0, items.size - 1)
        backup = null
    }

    /**
     * Set a single media item as the queue.
     */
    fun setMediaItem(item: PlatformMediaItem) {
        _queue.value = listOf(item)
        _currentIndex.value = 0
        backup = null
    }

    /**
     * Set current playback index.
     */
    fun setIndex(index: Int) {
        val currentQueue = _queue.value
        _currentIndex.value = index.coerceIn(0, currentQueue.size - 1)
    }

    /**
     * Append an item to the end of the queue.
     * If the queue is shuffled, the item is added to the backup queue as is
     * and shuffled before being added to the play queue.
     */
    fun addItem(item: PlatformMediaItem) {
        var itemToAdd = item
        if (backup != null) {
            // Add to backup queue unshuffled
            backup!!.add(item)
            // Shuffle the new item before adding to the play queue
            val shuffledList = mutableListOf(item)
            shuffledList.shuffle()
            itemToAdd = shuffledList[0]
        }
        _queue.value = _queue.value + itemToAdd
    }

    /**
     * Insert an item at a specific position.
     */
    fun insertItem(index: Int, item: PlatformMediaItem) {
        val currentQueue = _queue.value
        val newQueue = currentQueue.toMutableList()
        newQueue.add(index.coerceIn(0, currentQueue.size), item)

        // Adjust current index if insertion is before current item
        val currentIndexValue = _currentIndex.value
        if (index <= currentIndexValue) {
            _currentIndex.value = currentIndexValue + 1
        }

        _queue.value = newQueue
    }

    /**
     * Remove an item at the specified index.
     * Also removes the item from the backup queue if it exists.
     */
    fun removeItem(index: Int) {
        val currentQueue = _queue.value
        if (index in currentQueue.indices) {
            val item = currentQueue[index]
            val newQueue = currentQueue.toMutableList()
            newQueue.removeAt(index)

            // Adjust current index
            val currentIndexValue = _currentIndex.value
            when {
                index < currentIndexValue -> _currentIndex.value = currentIndexValue - 1
                index == currentIndexValue && index < newQueue.size -> {
                    // Keep at same position, now points to next item
                    _currentIndex.value = currentIndexValue
                }
                index == currentIndexValue -> {
                    // Last item removed, go to previous
                    _currentIndex.value = maxOf(0, currentIndexValue - 1)
                }
            }

            // Also remove from backup queue if it exists
            if (backup != null) {
                backup!!.remove(item)
            }

            _queue.value = newQueue
        }
    }

    /**
     * Move an item from one position to another.
     * Also moves the item in the backup queue if it exists.
     */
    fun moveItem(from: Int, to: Int) {
        val currentQueue = _queue.value
        val currentIndexValue = _currentIndex.value

        if (from in currentQueue.indices && to in currentQueue.indices && from != to) {
            val newQueue = currentQueue.toMutableList()
            val item = newQueue.removeAt(from)
            newQueue.add(to, item)

            // Adjust current index if needed
            when {
                from == currentIndexValue -> _currentIndex.value = to
                from < currentIndexValue && to >= currentIndexValue -> _currentIndex.value = currentIndexValue - 1
                from > currentIndexValue && to <= currentIndexValue -> _currentIndex.value = currentIndexValue + 1
            }

            // Also move in backup queue if it exists
            if (backup != null) {
                val backupIndex = backup!!.indexOf(item)
                if (backupIndex >= 0) {
                    backup!!.removeAt(backupIndex)
                    backup!!.add(to.coerceIn(0, backup!!.size), item)
                }
            }

            _queue.value = newQueue
        }
    }

    /**
     * Clear the entire queue.
     */
    fun clear() {
        _queue.value = emptyList()
        _currentIndex.value = 0
        backup = null
    }

    /**
     * Shuffle the queue with the currently playing item at the front.
     * Following NewPipe's approach:
     * - Create a backup if it doesn't already exist
     * - Shuffle the entire queue
     * - Move the currently playing item to the head (index 0)
     *
     */
    fun shuffle() {
        val currentQueue = _queue.value

        // Create a backup if it doesn't already exist
        // Note: The backup has to be created at all cost (even when size <= 2).
        // Otherwise it's not possible to enter shuffle-mode!
        if (backup == null) {
            backup = mutableListOf()
            backup!!.addAll(currentQueue)
        }
        // Can't shuffle a list that's empty or only has one element
        if (currentQueue.size <= 2) {
            return
        }

        val currentItem = currentQueue[_currentIndex.value]

        // Shuffle the entire queue
        val newQueue = currentQueue.toMutableList()
        newQueue.shuffle()

        // Move currentItem to the head of the queue
        newQueue.remove(currentItem)
        newQueue.add(0, currentItem)

        _queue.value = newQueue
        _currentIndex.value = 0
    }

    /**
     * Unshuffle the queue if a backup queue exists.
     * Following NewPipe's approach:
     * - Replace the current queue with the backup
     * - Clear the backup
     *
     */
    fun unshuffle() {
        if (backup == null) {
            return
        }

        val currentItem = getCurrentItem()

        // Restore from backup and clear it
        val restoredQueue = backup!!.toList()
        _queue.value = restoredQueue

        // Find current item's index in the original queue
        val newIndex = if (currentItem != null) {
            restoredQueue.indexOfFirst { it.mediaId == currentItem.mediaId }
        } else {
            0
        }

        _currentIndex.value = if (newIndex >= 0) newIndex else 0
        backup = null
    }
}
