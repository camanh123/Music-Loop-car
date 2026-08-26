package com.musicloop.car.library

import java.util.Locale

enum class LibraryTab {
    MUSIC,
    VIDEO,
    FAVORITES,
    PLAYLISTS
}

enum class LibrarySort {
    A_Z,
    Z_A,
    NEWEST,
    OLDEST,
    ARTIST,
    ALBUM
}

/**
 * In-memory library search/sort. Operates on already-loaded Room rows.
 * No USB I/O, no scanner, no Room queries.
 */
object LibraryListQuery {
    fun mediaType(tab: LibraryTab): String = if (tab == LibraryTab.VIDEO) "VIDEO" else "AUDIO"

    fun matchesSearch(row: MediaListRow, query: String, tab: LibraryTab): Boolean {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) {
            return true
        }
        return when (tab) {
            LibraryTab.MUSIC, LibraryTab.FAVORITES, LibraryTab.PLAYLISTS ->
                contains(row.title, needle) ||
                    contains(row.artist, needle) ||
                    contains(row.album, needle) ||
                    contains(row.fileName, needle)
            LibraryTab.VIDEO -> contains(row.title, needle) || contains(row.fileName, needle)
        }
    }

    fun apply(
        rows: List<MediaListRow>,
        tab: LibraryTab,
        query: String,
        sort: LibrarySort
    ): List<MediaListRow> {
        val type = mediaType(tab)
        val filtered = rows.filter { row ->
            row.mediaType == type && matchesSearch(row, query, tab)
        }
        return sort(filtered, sort)
    }

    fun sort(rows: List<MediaListRow>, sort: LibrarySort): List<MediaListRow> {
        val byTitle = compareBy<MediaListRow> { displayTitle(it) }.thenBy { it.fileName.lowercase(Locale.ROOT) }
        return when (sort) {
            LibrarySort.A_Z -> rows.sortedWith(byTitle)
            LibrarySort.Z_A -> rows.sortedWith(byTitle.reversed())
            LibrarySort.NEWEST -> rows.sortedWith(
                compareByDescending<MediaListRow> { it.modifiedTime }.then(byTitle)
            )
            LibrarySort.OLDEST -> rows.sortedWith(
                compareBy<MediaListRow> { it.modifiedTime }.then(byTitle)
            )
            LibrarySort.ARTIST -> rows.sortedWith(
                compareBy<MediaListRow> { it.artist.orEmpty().ifBlank { "\uFFFF" }.lowercase(Locale.ROOT) }
                    .then(byTitle)
            )
            LibrarySort.ALBUM -> rows.sortedWith(
                compareBy<MediaListRow> { it.album.orEmpty().ifBlank { "\uFFFF" }.lowercase(Locale.ROOT) }
                    .then(byTitle)
            )
        }
    }

    fun isCurrent(row: MediaListRow, volumeId: String?, relativePath: String?): Boolean {
        if (volumeId.isNullOrEmpty() || relativePath.isNullOrEmpty()) {
            return false
        }
        return row.volumeId == volumeId && row.relativePath == relativePath
    }

    fun displayTitle(row: MediaListRow): String {
        return (row.title?.takeIf { it.isNotBlank() } ?: row.fileName).lowercase(Locale.ROOT)
    }

    private fun contains(value: String?, needle: String): Boolean {
        return value?.lowercase(Locale.ROOT)?.contains(needle) == true
    }
}
