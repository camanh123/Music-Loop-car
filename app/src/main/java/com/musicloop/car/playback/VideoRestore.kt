package com.musicloop.car.playback

import com.musicloop.car.library.MediaListRow

/**
 * Pure restore helpers for the video library list. No USB I/O.
 */
object VideoRestore {
    fun matches(row: MediaListRow, saved: VideoPlaybackState): Boolean {
        return saved.hasIdentity &&
            row.volumeId == saved.volumeId &&
            row.relativePath == saved.relativePath
    }

    fun indexOf(items: List<MediaListRow>, volumeId: String, relativePath: String): Int {
        return items.indexOfFirst { row ->
            row.volumeId == volumeId && row.relativePath == relativePath
        }
    }

    /**
     * Find the last video by volumeId + relativePath. If it is gone, use the first
     * available video. Returns null only when the library has no videos.
     */
    fun resolveCurrent(items: List<MediaListRow>, saved: VideoPlaybackState): RestoreTarget? {
        if (items.isEmpty()) {
            return null
        }
        if (saved.hasIdentity) {
            val index = indexOf(items, saved.volumeId, saved.relativePath)
            if (index >= 0) {
                return RestoreTarget(item = items[index], index = index, matchedSaved = true)
            }
        }
        return RestoreTarget(item = items.first(), index = 0, matchedSaved = false)
    }

    fun adjacent(items: List<MediaListRow>, current: MediaListRow?, delta: Int): MediaListRow? {
        if (current == null || items.isEmpty() || delta == 0) {
            return null
        }
        val index = indexOf(items, current.volumeId, current.relativePath)
        if (index < 0) {
            return null
        }
        return items.getOrNull(index + delta)
    }

    fun clampScroll(listPosition: Int, itemCount: Int): Int {
        if (itemCount <= 0) {
            return 0
        }
        return listPosition.coerceIn(0, itemCount - 1)
    }

    data class RestoreTarget(
        val item: MediaListRow,
        val index: Int,
        val matchedSaved: Boolean
    )
}
