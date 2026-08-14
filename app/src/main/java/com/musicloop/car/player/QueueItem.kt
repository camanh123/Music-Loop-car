package com.musicloop.car.player

/**
 * One row in the current library queue (SONGS list).
 * Identity is volume + relative path, never a stale USB absolute path.
 */
data class QueueItem(
    val id: Long,
    val volumeIdentity: String,
    val relativePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long?,
    val filename: String,
    val extension: String,
    val playable: Boolean
) {
    fun sameTrack(other: QueueItem?): Boolean {
        if (other == null) return false
        return volumeIdentity == other.volumeIdentity && relativePath == other.relativePath
    }
}
