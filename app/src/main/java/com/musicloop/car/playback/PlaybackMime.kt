package com.musicloop.car.playback

import com.musicloop.car.storage.MediaExtensions

object PlaybackMime {
    fun fromFileName(fileName: String): String? {
        return when (MediaExtensions.extensionOf(fileName)) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/mp4"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "ts" -> "video/mp2t"
            else -> null
        }
    }

    fun isVideoFileName(fileName: String): Boolean {
        return fromFileName(fileName)?.startsWith("video/") == true
    }
}
