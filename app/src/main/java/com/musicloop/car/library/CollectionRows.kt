package com.musicloop.car.library

import com.musicloop.car.database.FavoriteEntity
import com.musicloop.car.database.PlaylistItemEntity
import com.musicloop.car.database.PlaylistRecord

object CollectionRows {
    fun fromFavorites(
        favorites: List<FavoriteEntity>,
        library: List<MediaListRow>,
        usbOnline: Boolean,
        query: String,
        sort: LibrarySort
    ): List<MediaListRow> {
        val index = library.associateBy { it.volumeId to it.relativePath }
        val rows = favorites.map { favorite ->
            val live = index[favorite.volumeId to favorite.relativePath]
            val available = usbOnline && live != null
            (live ?: favorite.toRow()).copy(available = available)
        }
        val searched = rows.filter { LibraryListQuery.matchesSearch(it, query, LibraryTab.MUSIC) }
        return LibraryListQuery.sort(searched, sort)
    }

    fun fromPlaylistItems(
        items: List<PlaylistItemEntity>,
        library: List<MediaListRow>,
        usbOnline: Boolean,
        query: String
    ): List<MediaListRow> {
        val index = library.associateBy { it.volumeId to it.relativePath }
        val rows = items.map { item ->
            val live = index[item.volumeId to item.relativePath]
            val available = usbOnline && live != null
            (live ?: item.toRow()).copy(available = available)
        }
        return rows.filter { LibraryListQuery.matchesSearch(it, query, LibraryTab.MUSIC) }
    }

    fun playlistSummaries(playlists: List<PlaylistRecord>, query: String): List<MediaListRow> {
        val needle = query.trim()
        return playlists
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .map { it.toRow() }
    }
}

private fun FavoriteEntity.toRow(): MediaListRow {
    return MediaListRow(
        id = (volumeId + relativePath).hashCode().toLong(),
        volumeId = volumeId,
        relativePath = relativePath,
        fileName = fileName,
        extension = fileName.substringAfterLast('.', ""),
        mediaType = mediaType,
        sizeBytes = 0L,
        durationMs = null,
        title = title,
        artist = artist,
        album = album,
        scanStatus = "FAVORITE"
    )
}

private fun PlaylistItemEntity.toRow(): MediaListRow {
    return MediaListRow(
        id = (playlistId.toString() + volumeId + relativePath).hashCode().toLong(),
        volumeId = volumeId,
        relativePath = relativePath,
        fileName = fileName,
        extension = fileName.substringAfterLast('.', ""),
        mediaType = mediaType,
        sizeBytes = 0L,
        durationMs = null,
        title = title,
        artist = artist,
        album = album,
        scanStatus = "PLAYLIST"
    )
}

private fun PlaylistRecord.toRow(): MediaListRow {
    return MediaListRow(
        id = id,
        volumeId = "playlist",
        relativePath = id.toString(),
        fileName = name,
        extension = "",
        mediaType = "PLAYLIST",
        sizeBytes = 0L,
        durationMs = null,
        title = name,
        artist = "$itemCount",
        album = null,
        scanStatus = "PLAYLIST",
        available = true
    )
}
