package com.musicloop.car.database

import com.musicloop.car.library.MediaListRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomUserCollectionsRepository(
    database: AppDatabase
) : UserCollectionsRepository {
    private val favorites = database.favoriteDao()
    private val playlists = database.playlistDao()
    private val items = database.playlistItemDao()

    override suspend fun isFavorite(volumeId: String, relativePath: String): Boolean {
        return favorites.count(volumeId, relativePath) > 0
    }

    override suspend fun setFavorite(row: MediaListRow, favorite: Boolean) {
        if (favorite) {
            favorites.upsert(
                FavoriteEntity(
                    volumeId = row.volumeId,
                    relativePath = row.relativePath,
                    fileName = row.fileName,
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    mediaType = row.mediaType,
                    addedAt = System.currentTimeMillis()
                )
            )
        } else {
            favorites.remove(row.volumeId, row.relativePath)
        }
    }

    override suspend fun favorites(): List<FavoriteEntity> = favorites.getAll()

    override fun observeFavorites(): Flow<List<FavoriteEntity>> = favorites.observeAll()

    override suspend fun createPlaylist(name: String, nowMs: Long): PlaylistRecord {
        val id = playlists.insert(
            PlaylistEntity(
                name = name.trim().ifBlank { "Playlist" },
                createdAt = nowMs,
                updatedAt = nowMs
            )
        )
        return PlaylistRecord(id, name.trim().ifBlank { "Playlist" }, 0, nowMs, nowMs)
    }

    override suspend fun renamePlaylist(id: Long, name: String, nowMs: Long) {
        val existing = playlists.getById(id) ?: return
        playlists.update(
            existing.copy(
                name = name.trim().ifBlank { existing.name },
                updatedAt = nowMs
            )
        )
    }

    override suspend fun removePlaylist(id: Long) {
        items.removeForPlaylist(id)
        playlists.remove(id)
    }

    override suspend fun playlists(): List<PlaylistRecord> {
        return playlists.getAll().map { it.toRecord(items.getForPlaylist(it.id).size) }
    }

    override fun observePlaylists(): Flow<List<PlaylistRecord>> {
        return combine(playlists.observeAll(), items.observeAll()) { rows, allItems ->
            val grouped = allItems.groupBy { it.playlistId }
            rows.map { playlist -> playlist.toRecord(grouped[playlist.id]?.size ?: 0) }
        }
    }

    override suspend fun addToPlaylist(playlistId: Long, row: MediaListRow, nowMs: Long) {
        val nextPos = items.maxPosition(playlistId) + 1
        items.upsert(
            PlaylistItemEntity(
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
        )
        val existing = playlists.getById(playlistId) ?: return
        playlists.update(existing.copy(updatedAt = nowMs))
    }

    override suspend fun removeFromPlaylist(playlistId: Long, volumeId: String, relativePath: String) {
        items.removeItem(playlistId, volumeId, relativePath)
    }

    override suspend fun playlistItems(playlistId: Long): List<PlaylistItemEntity> =
        items.getForPlaylist(playlistId)

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        items.observeForPlaylist(playlistId)
}

private fun PlaylistEntity.toRecord(itemCount: Int): PlaylistRecord {
    return PlaylistRecord(
        id = id,
        name = name,
        itemCount = itemCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
