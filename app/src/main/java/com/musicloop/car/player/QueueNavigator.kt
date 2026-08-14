package com.musicloop.car.player

enum class PreviousAction {
    RESTART,
    PREVIOUS
}

data class QueueWalkResult(
    val index: Int? = null,
    val exhausted: Boolean = false,
    val ended: Boolean = false
)

/**
 * Pure queue math for the current SONGS list.
 * Does not delete database rows and does not touch USB.
 */
object QueueNavigator {
    const val RESTART_THRESHOLD_MS = 5000

    fun previousAction(positionMs: Int, thresholdMs: Int = RESTART_THRESHOLD_MS): PreviousAction {
        return if (positionMs > thresholdMs) PreviousAction.RESTART else PreviousAction.PREVIOUS
    }

    fun nextIndex(
        size: Int,
        fromIndex: Int,
        wrap: Boolean
    ): QueueWalkResult {
        return walk(size, fromIndex, direction = 1, wrap = wrap)
    }

    fun previousIndex(
        size: Int,
        fromIndex: Int,
        wrap: Boolean
    ): QueueWalkResult {
        return walk(size, fromIndex, direction = -1, wrap = wrap)
    }

    /**
     * Walk from [fromIndex] in [direction], skipping indices rejected by [canPlay].
     * Completion with Repeat OFF uses wrap=false so the last track ends the queue.
     * Next/Previous buttons use wrap=true.
     */
    fun findPlayable(
        size: Int,
        fromIndex: Int,
        direction: Int,
        wrap: Boolean,
        canPlay: (Int) -> Boolean
    ): QueueWalkResult {
        if (size <= 0) {
            return QueueWalkResult(exhausted = true)
        }
        var index = fromIndex
        var steps = 0
        while (steps < size) {
            val walked = walk(size, index, direction, wrap)
            if (walked.ended || walked.index == null) {
                return walked
            }
            index = walked.index
            steps++
            if (canPlay(index)) {
                return QueueWalkResult(index = index)
            }
        }
        return QueueWalkResult(exhausted = true)
    }

    private fun walk(
        size: Int,
        fromIndex: Int,
        direction: Int,
        wrap: Boolean
    ): QueueWalkResult {
        if (size <= 0) {
            return QueueWalkResult(exhausted = true)
        }
        val next = fromIndex + direction
        if (next in 0 until size) {
            return QueueWalkResult(index = next)
        }
        if (!wrap) {
            return QueueWalkResult(ended = true)
        }
        val wrapped = if (direction > 0) 0 else size - 1
        return QueueWalkResult(index = wrapped)
    }
}
