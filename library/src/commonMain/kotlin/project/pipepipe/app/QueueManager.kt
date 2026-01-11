package project.pipepipe.app

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import project.pipepipe.app.platform.PlatformMediaItem
import project.pipepipe.app.platform.RepeatMode

class QueueManager {
    private val _queue = MutableStateFlow<List<PlatformMediaItem>>(emptyList())
    val queue: StateFlow<List<PlatformMediaItem>> = _queue.asStateFlow()

    val mediaItemCount: StateFlow<Int> = _queue.map { it.size }.stateIn(GlobalScope, SharingStarted.Eagerly, 0)

    private var backup: MutableList<PlatformMediaItem>? = null

    fun getCurrentQueue(): List<PlatformMediaItem> = _queue.value

    fun getIndexOfItemUUID(uuid: String): Int {
        val currentQueue = _queue.value
        return currentQueue.indexOfFirst { it.uuid == uuid }
    }

    fun isShuffled(): Boolean = backup != null

    fun setQueue(items: List<PlatformMediaItem>, startIndex: Int = 0, notifyOnly: Boolean = false) {
        _queue.value = items.toList()
        backup = null
        if (!notifyOnly) {
            SharedContext.platformMediaController!!.setQueue(items, startIndex)
        }
    }

    fun addItem(item: PlatformMediaItem) {
        var itemToAdd = item
        if (backup != null) {
            backup!!.add(item)
            val shuffledList = mutableListOf(item)
            shuffledList.shuffle()
            itemToAdd = shuffledList[0]
        }
        _queue.value = _queue.value + itemToAdd

        SharedContext.platformMediaController!!.syncQueueAppend(itemToAdd)
    }

    fun removeItemByUuid(uuid: String) {
        val currentQueue = _queue.value
        val index = currentQueue.indexOfFirst { it.uuid == uuid }
        if (index < 0) return

        val newQueue = currentQueue.toMutableList()
        newQueue.removeAt(index)
        _queue.value = newQueue

        if (backup != null) {
            val backupIndex = backup!!.indexOfFirst { it.uuid == uuid }
            if (backupIndex >= 0) {
                backup!!.removeAt(backupIndex)
            }
        }

        SharedContext.platformMediaController!!.syncQueueRemove(index)
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
        if (from < 0 || from >= currentQueue.size || to < 0 || to >= currentQueue.size || from == to) {
            return
        }

        val newQueue = currentQueue.toMutableList()
        val item = newQueue.removeAt(from)
        newQueue.add(to, item)
        _queue.value = newQueue

        if (backup != null) {
            val backupSize = backup!!.size
            val adjustedFrom = if (from < backupSize) from else -1
            val adjustedTo = if (to < backupSize) to else -1

            if (adjustedFrom >= 0 && adjustedTo >= 0 && adjustedFrom != adjustedTo) {
                val backupItem = backup!!.removeAt(adjustedFrom)
                backup!!.add(adjustedTo, backupItem)
            }
        }

        SharedContext.platformMediaController!!.syncQueueMove(from, to)
    }


    fun clear() {
        _queue.value = emptyList()
        backup = null
        SharedContext.platformMediaController!!.syncQueueClear()
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

        val currentUuid: String = SharedContext.platformMediaController?.currentMediaItem?.value?.uuid?:return
        val currentIndex = getIndexOfItemUUID(currentUuid)
        if (currentIndex == -1) return
        val currentItem = getCurrentQueue()[currentIndex]

        val newQueue = currentQueue.toMutableList()
        newQueue.shuffle()

        newQueue.remove(currentItem)
        newQueue.add(0, currentItem)

        _queue.value = newQueue
        SharedContext.platformMediaController!!.syncQueueShuffle()
    }

    fun unshuffle() {
        if (backup == null) {
            return
        }
        val restoredQueue = backup!!.toList()
        _queue.value = restoredQueue
        backup = null
        SharedContext.platformMediaController!!.syncQueueShuffle()
    }
}
