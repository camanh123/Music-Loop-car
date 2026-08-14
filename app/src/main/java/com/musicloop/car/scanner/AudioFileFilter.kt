package com.musicloop.car.scanner

object AudioFileFilter {
    private val extensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac")

    fun isAudioFile(filename: String): Boolean {
        val ext = extensionOf(filename) ?: return false
        return ext in extensions
    }

    fun extensionOf(filename: String): String? {
        val dot = filename.lastIndexOf('.')
        if (dot <= 0 || dot == filename.length - 1) {
            return null
        }
        return filename.substring(dot + 1).lowercase()
    }
}

object FilenameTitleParser {
    /**
     * Fallback title is the filename without extension.
     * Track-number prefixes are kept (Phase 3 spec).
     */
    fun titleFromFilename(filename: String): String {
        val dot = filename.lastIndexOf('.')
        return if (dot > 0) filename.substring(0, dot) else filename
    }
}

object DurationFormatter {
    fun format(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0L) {
            return "--:--"
        }
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }
}
