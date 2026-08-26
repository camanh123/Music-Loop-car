package com.musicloop.car.database

import com.musicloop.car.library.MediaListRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryUserCollectionsRepository : UserCollectionsRepository {
    private var playlistSeq = 1L
    private val favoriteRows = LinkedHashMap<MediaKey, FavoriteEntity>()
    private val playlistRows = LinkedHashMap<Long, PlaylistEntity>()
    private val itemRows = LinkedHashMap<Long, MutableList<PlaylistItemEntity>>()
    private val favoritesFlow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    private val playlistsFlow = MutableStateFlow<List<PlaylistRecord>>(emptyList())
    private val itemsFlow = MutableStateFlow<Map<Long, List<PlaylistItemEntity>>>(emptyMap())

    override suspend fun isFavorite(volumeId: String, relativePath: String): Boolean {
        return favoriteRows.containsKey(MediaKey(volumeId, relativePath))
    }

    override suspend fun setFavorite(row: MediaListRow, favorite: Boolean) {
        val key = MediaKey(row.volumeId, row.relativePath)
        if (favorite) {
            favoriteRows[key] = FavoriteEntity(
                volumeId = row.volumeId,
                relativePath = row.relativePath,
                fileName = row.fileName,
                title = row.title,
                artist = row.artist,
                album = row.album,
                mediaType = row.mediaType,
                addedAt = System.currentTimeMillis()
            )
        } else {
            favoriteRows.remove(key)
        }
        publishFavorites()
    }

    override suspend fun favorites(): List<FavoriteEntity> = favoriteRows.values.toList()

    override fun observeFavorites(): Flow<List<FavoriteEntity>> = favoritesFlow

    override suspend fun createPlaylist(name: String, nowMs: Long): PlaylistRecord {
        val id = playlistSeq++
        val trimmed = name.trim().ifBlank { "Playlist" }
        playlistRows[id] = PlaylistEntity(id, trimmed, nowMs, nowMs)
        itemRows[id] = mutableListOf()
        publishPlaylists()
        return PlaylistRecord(id, trimmed, 0, nowMs, nowMs)
    }

    override suspend fun renamePlaylist(id: Long, name: String, nowMs: Long) {
        val existing = playlistRows[id] ?: return
        playlistRows[id] = existing.copy(
            name = name.trim().ifBlank { existing.name },
            updatedAt = nowMs
        )
        publishPlaylists()
    }

    override suspend fun removePlaylist(id: Long) {
        playlistRows.remove(id)
        itemRows.remove(id)
        publishPlaylists()
        publishItems()
    }

    override suspend fun playlists(): List<PlaylistRecord> = playlistsFlow.value

    override fun observePlaylists(): Flow<List<PlaylistRecord>> = playlistsFlow

    override suspend fun addToPlaylist(playlistId: Long, row: MediaListRow, nowMs: Long) {
        val list = itemRows[playlistId] ?: return
        val nextPos = (list.maxOfOrNull { it.position } ?: -1) + 1
        list.removeAll { it.volumeId == row.volumeId && it.relativePath == row.relativePath }
        list += PlaylistItemEntity(
            playlistId = playlistId,
            position = nextPos,
            volumeId = row.volumeId,
            relativePath = row.relativePath,
            fileName = row.fileName,
            title = row.title,
            artist = row.artist,
            album = row.album,
            mediaType = row.mediaType,
            addedAt = nowMs
        )
        playlistRows[playlistId]?.let { playlistRows[playlistId] = it.copy(updatedAt = nowMs) }
        publishPlaylists()
        publishItems()
    }

    override suspend fun removeFromPlaylist(playlistId: Long, volumeId: String, relativePath: String) {
        val list = itemRows[playlistId] ?: return
        list.removeAll { it.volumeId == volumeId && it.relativePath == relativePath }
        publishPlaylists()
        publishItems()
    }

    override suspend fun playlistItems(playlistId: Long): List<PlaylistItemEntity> {
        return itemRows[playlistId]?.toList().orEmpty()
    }

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> {
        return itemsFlow.map { it[playlistId].orEmpty() }
    }

    private fun publishFavorites() {
        favoritesFlow.value = favoriteRows.values.toList()
    }

    private fun publishPlaylists() {
        playlistsFlow.value = playlistRows.values.map { playlist ->
            PlaylistRecord(
                id = playlist.id,
                name = playlist.name,
                itemCount = itemRows[playlist.id]?.size ?: 0,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt
            )
        }.sortedByDescending { it.updatedAt }
    }

    private fun publishItems() {
        itemsFlow.value = itemRows.mapValues { it.value.toList() }
    }
}
