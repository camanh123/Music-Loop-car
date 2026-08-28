package com.musicloop.car.library

object MediaFileInfo {
    fun lines(row: MediaListRow): List<Pair<String, String>> {
        val lines = mutableListOf<Pair<String, String>>()
        lines += "Tên" to (row.title?.takeIf { it.isNotBlank() } ?: row.fileName)
        if (row.mediaType == "AUDIO") {
            val artist = row.artist?.takeIf { it.isNotBlank() }
            if (artist != null) {
                lines += "Nghệ sĩ" to artist
            }
        }
        lines += "Tệp" to row.fileName
        lines += "Loại" to if (row.mediaType == "VIDEO") "Video" else "Nhạc"
        lines += "Dung lượng" to DeleteMessages.formatSize(row.sizeBytes)
        row.durationMs?.takeIf { it > 0L }?.let { duration ->
            lines += "Thời lượng" to formatDuration(duration)
        }
        lines += "Đường dẫn USB" to row.relativePath
        return lines
    }

    fun format(row: MediaListRow): String {
        return lines(row).joinToString("\n\n") { (label, value) -> "$label:\n$value" }
    }

    fun formatDuration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
