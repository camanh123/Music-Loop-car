package com.musicloop.car.library

import java.util.Locale

/**
 * List-row copy. No USB I/O. Video hides a second line when title and
 * filename are the same name.
 */
object MediaRowText {
    const val UNAVAILABLE = "Unavailable"

    fun title(row: MediaListRow): String {
        return row.title?.takeIf { it.isNotBlank() } ?: row.fileName
    }

    fun subtitle(row: MediaListRow): String? {
        if (!row.available) {
            return UNAVAILABLE
        }
        if (row.mediaType == "PLAYLIST") {
            return row.artist?.let { "$it songs" } ?: row.fileName
        }
        if (row.mediaType == "VIDEO") {
            return if (isDuplicateTitleAndFile(row.title, row.fileName)) {
                null
            } else {
                row.fileName.takeIf { it.isNotBlank() && it != title(row) }
            }
        }
        return row.artist?.takeIf { it.isNotBlank() }
    }

    fun isDuplicateTitleAndFile(title: String?, fileName: String): Boolean {
        val shown = title?.takeIf { it.isNotBlank() } ?: fileName
        return normalize(shown) == normalize(fileName)
    }

    fun normalize(value: String): String {
        val trimmed = value.trim()
        val dot = trimmed.lastIndexOf('.')
        val stem = if (dot > 0) trimmed.substring(0, dot) else trimmed
        return stem.lowercase(Locale.ROOT)
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
