package com.musicloop.car.playback

/**
 * Throttles playback-position persistence so we do not write on every poll tick.
 */
class PositionSaveGate(
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
    private val minDeltaMs: Long = DEFAULT_DELTA_MS
) {
    private var lastSavedAtMs: Long = 0L
    private var lastSavedPositionMs: Long = -1L

    fun shouldSave(nowMs: Long, positionMs: Long, force: Boolean): Boolean {
        if (force) {
            remember(nowMs, positionMs)
            return true
        }
        val intervalOk = lastSavedAtMs == 0L || nowMs - lastSavedAtMs >= minIntervalMs
        val deltaOk = lastSavedPositionMs < 0L ||
            kotlin.math.abs(positionMs - lastSavedPositionMs) >= minDeltaMs
        if (intervalOk && deltaOk) {
            remember(nowMs, positionMs)
            return true
        }
        return false
    }

    private fun remember(nowMs: Long, positionMs: Long) {
        lastSavedAtMs = nowMs
        lastSavedPositionMs = positionMs
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 5_000L
        const val DEFAULT_DELTA_MS = 1_000L
    }
}
