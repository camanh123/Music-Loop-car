package com.musicloop.car.player

import kotlin.random.Random

/**
 * Builds an in-memory shuffle order. Does not reorder Room rows or touch USB.
 */
object ShuffleQueue {

    fun build(
        source: List<QueueItem>,
        current: QueueItem?,
        random: Random = Random.Default
    ): List<QueueItem> {
        val playable = source.filter { it.playable }
        if (playable.size <= 1) {
            return playable
        }
        val currentItem = current?.let { playing -> playable.find { it.sameTrack(playing) } }
        val rest = playable.filter { currentItem == null || !it.sameTrack(currentItem) }
            .shuffled(random)
        return if (currentItem != null) listOf(currentItem) + rest else rest
    }

    fun reshuffleExcludingCurrentFirst(
        source: List<QueueItem>,
        justFinished: QueueItem?,
        random: Random = Random.Default
    ): List<QueueItem> {
        val playable = source.filter { it.playable }
        if (playable.size <= 1) {
            return playable
        }
        val shuffled = playable.shuffled(random).toMutableList()
        if (justFinished != null && shuffled.size > 1 && shuffled.first().sameTrack(justFinished)) {
            val swapWith = 1 + random.nextInt(shuffled.size - 1)
            val first = shuffled[0]
            shuffled[0] = shuffled[swapWith]
            shuffled[swapWith] = first
        }
        return shuffled
    }

    fun prune(queue: List<QueueItem>, source: List<QueueItem>): List<QueueItem> {
        return queue.mapNotNull { item -> source.find { it.sameTrack(item) } }
    }
}
