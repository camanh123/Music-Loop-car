package com.musicloop.car.storage

enum class MediaKind {
    AUDIO,
    VIDEO
}

/**
 * Extension whitelist for the Phase 1 USB read benchmark.
 * Case-insensitive. MP4 is included as video only.
 */
object MediaExtensions {
    val AUDIO = setOf("mp3", "flac", "aac", "m4a", "wav", "ogg")
    val VIDEO = setOf("mp4", "mkv", "avi", "ts")

    fun extensionOf(filename: String): String? {
        val dot = filename.lastIndexOf('.')
        if (dot <= 0 || dot == filename.length - 1) {
            return null
        }
        return filename.substring(dot + 1).lowercase()
    }

    fun kindOf(filename: String): MediaKind? {
        val ext = extensionOf(filename) ?: return null
        return when (ext) {
            in AUDIO -> MediaKind.AUDIO
            in VIDEO -> MediaKind.VIDEO
            else -> null
        }
    }
}
