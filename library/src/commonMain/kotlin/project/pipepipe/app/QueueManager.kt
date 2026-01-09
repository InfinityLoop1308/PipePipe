package project.pipepipe.app

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import project.pipepipe.app.platform.PlatformMediaItem
import project.pipepipe.app.platform.RepeatMode

class QueueManager {
    private val _queue = MutableStateFlow<List<PlatformMediaItem>>(emptyList())
    val queue: StateFlow<List<PlatformMediaItem>> = _queue.asStateFlow()

    val mediaItemCount: StateFlow<Int> = _queue.map { it.size }.stateIn(GlobalScope, SharingStarted.Eagerly, 0)


    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    val isLastIndex: Boolean get() = currentIndex.value == queue.value.size - 1

    val currentItem: StateFlow<PlatformMediaItem?> =
        combine(_queue, _currentIndex) { queue, index ->
            queue.getOrNull(index)
        }.stateIn(GlobalScope, SharingStarted.Eagerly, null)

    private var backup: MutableList<PlatformMediaItem>? = null

    fun getCurrentQueue(): List<PlatformMediaItem> = _queue.value

    fun getCurrentIndex(): Int = _currentIndex.value

    fun getCurrentItem(): PlatformMediaItem? = getCurrentQueue().getOrNull(_currentIndex.value)

    fun getIndexOfItem(mediaId: String): Int {
        val currentQueue = _queue.value
        return currentQueue.indexOfFirst { it.mediaId == mediaId }
    }

    fun getIndexOfItemUUID(uuid: String): Int {
        val currentQueue = _queue.value
        return currentQueue.indexOfFirst { it.uuid == uuid }
    }

    fun getNextItem(): PlatformMediaItem? {
        val repeatMode = SharedContext.platformMediaController?.repeatMode?.value?: RepeatMode.OFF
        val currentQueue = _queue.value
        val index = _currentIndex.value

        return when (repeatMode) {
            RepeatMode.ALL -> {
                if (currentQueue.isEmpty()) null
                else currentQueue[(index + 1) % currentQueue.size]
            }
            RepeatMode.OFF, RepeatMode.ONE -> {
                if (index + 1 < currentQueue.size) currentQueue[index + 1]
                else null
            }
        }
    }

    fun getPreviousItem(): PlatformMediaItem? {
        val currentQueue = _queue.value
        val index = _currentIndex.value

        return if (index > 0) currentQueue[index - 1] else null
    }

    fun getCurrentThreeElementQueue(): List<PlatformMediaItem>  = listOfNotNull(getPreviousItem(), getCurrentItem(), getNextItem())

    fun isShuffled(): Boolean = backup != null

    fun setQueue(items: List<PlatformMediaItem>, startIndex: Int = 0) {
        _queue.value = items.toList()
        _currentIndex.value = startIndex.coerceIn(0, items.size - 1)
        backup = null
        SharedContext.platformMediaController?.loadMediaQueueForCurrentItem()
    }

    fun setQueueSilent(items: List<PlatformMediaItem>, startIndex: Int = 0) {
        _queue.value = items.toList()
        _currentIndex.value = startIndex.coerceIn(0, items.size - 1)
        backup = null
        SharedContext.platformMediaController?.loadMediaQueueForCurrentItem()
    }

    fun setMediaItem(item: PlatformMediaItem) {
        _queue.value = listOf(item)
        _currentIndex.value = 0
        backup = null
        SharedContext.platformMediaController?.loadMediaQueueForCurrentItem()
    }

    /**
     * Set current playback index. DOESN'T refresh the queue
     */
    fun setIndex(index: Int) {
        val currentQueue = _queue.value
        _currentIndex.value = index.coerceIn(0, currentQueue.size - 1)
    }

    fun addItem(item: PlatformMediaItem) {
        val currentThreeElementQueue = getCurrentThreeElementQueue()

        var itemToAdd = item
        if (backup != null) {
            backup!!.add(item)
            val shuffledList = mutableListOf(item)
            shuffledList.shuffle()
            itemToAdd = shuffledList[0]
        }
        _queue.value = _queue.value + itemToAdd

        if (getCurrentThreeElementQueue() != currentThreeElementQueue) {
            SharedContext.platformMediaController?.loadMediaQueueForCurrentItem(shouldKeepPosition = true)
        }
    }

    fun insertItem(index: Int, item: PlatformMediaItem) {
        val currentThreeElementQueue = getCurrentThreeElementQueue()

        val currentQueue = _queue.value
        val newQueue = currentQueue.toMutableList()
        newQueue.add(index.coerceIn(0, currentQueue.size), item)

        val currentIndexValue = _currentIndex.value
        if (index <= currentIndexValue) {
            _currentIndex.value = currentIndexValue + 1
        }

        _queue.value = newQueue

        if (getCurrentThreeElementQueue() != currentThreeElementQueue) {
            SharedContext.platformMediaController?.loadMediaQueueForCurrentItem(shouldKeepPosition = true)
        }
    }

    fun removeItem(index: Int) {
        val currentQueue = _queue.value
        if (index in currentQueue.indices) {
            val currentThreeElementQueue = getCurrentThreeElementQueue()

            val item = currentQueue[index]
            val newQueue = currentQueue.toMutableList()
            newQueue.removeAt(index)

            val currentIndexValue = _currentIndex.value
            when {
                index < currentIndexValue -> _currentIndex.value = currentIndexValue - 1
                index == currentIndexValue && index < newQueue.size -> {
                    _currentIndex.value = currentIndexValue
                }
                index == currentIndexValue -> {
                    _currentIndex.value = maxOf(0, currentIndexValue - 1)
                }
            }

            if (backup != null) {
                backup!!.remove(item)
            }

            _queue.value = newQueue

            if (getCurrentThreeElementQueue() != currentThreeElementQueue) {
                SharedContext.platformMediaController?.loadMediaQueueForCurrentItem(shouldKeepPosition = true)
            }
        }
    }

    fun removeItemByUuid(uuid: String) {
        removeItem(getIndexOfItemUUID(uuid))
    }


    fun updateItemExtras(uuid: String, newExtras: Map<String, Any?>) {
        val currentQueue = _queue.value
        val index = currentQueue.indexOfFirst { it.uuid == uuid }
        if (index < 0) return

        val item = currentQueue[index]
        val mergedExtras = (item.extras ?: emptyMap()) + newExtras
        val updatedItem = item.copy(extras = mergedExtras.takeIf { it.isNotEmpty() })

        val newQueue = currentQueue.toMutableList()
        newQueue[index] = updatedItem
        _queue.value = newQueue

        if (backup != null) {
            val backupIndex = backup!!.indexOfFirst { it.uuid == uuid }
            if (backupIndex >= 0) {
                backup!![backupIndex] = updatedItem
            }
        }
    }

    fun moveItem(from: Int, to: Int) {
        val currentQueue = _queue.value
        val currentIndexValue = _currentIndex.value

        if (from !in currentQueue.indices || to !in currentQueue.indices || from == to) {
            return
        }

        val currentThreeElementQueue = getCurrentThreeElementQueue()
        val newQueue = currentQueue.toMutableList()
        val item = newQueue.removeAt(from)
        newQueue.add(to, item)

        val newIndex = when {
            from == currentIndexValue -> to
            from < currentIndexValue && to >= currentIndexValue -> currentIndexValue - 1
            from > currentIndexValue && to <= currentIndexValue -> currentIndexValue + 1
            else -> currentIndexValue
        }

        _currentIndex.value = newIndex
        _queue.value = newQueue

        if (getCurrentThreeElementQueue() != currentThreeElementQueue) {
            SharedContext.platformMediaController?.loadMediaQueueForCurrentItem(shouldKeepPosition = true)
        }
    }


    fun clear() {
        _queue.value = emptyList()
        _currentIndex.value = 0
        backup = null
    }

    fun shuffle() {
        val currentQueue = _queue.value

        if (backup == null) {
            backup = mutableListOf()
            backup!!.addAll(currentQueue)
        }
        if (currentQueue.size <= 2) {
            return
        }

        val currentItem = currentQueue[_currentIndex.value]

        val newQueue = currentQueue.toMutableList()
        newQueue.shuffle()

        newQueue.remove(currentItem)
        newQueue.add(0, currentItem)

        _queue.value = newQueue
        _currentIndex.value = 0
        SharedContext.platformMediaController?.loadMediaQueueForCurrentItem(shouldKeepPosition = true)
    }

    fun unshuffle() {
        if (backup == null) {
            return
        }

        val currentItem = getCurrentItem()

        val restoredQueue = backup!!.toList()
        _queue.value = restoredQueue

        val newIndex = if (currentItem != null) {
            restoredQueue.indexOfFirst { it.mediaId == currentItem.mediaId }
        } else {
            0
        }

        _currentIndex.value = if (newIndex >= 0) newIndex else 0
        backup = null
        SharedContext.platformMediaController?.loadMediaQueueForCurrentItem(shouldKeepPosition = true)
    }
}
