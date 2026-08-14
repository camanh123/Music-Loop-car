package com.musicloop.car.player

object PlaybackTime {
    fun format(positionMs: Int): String {
        val safe = positionMs.coerceAtLeast(0)
        val totalSeconds = safe / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
