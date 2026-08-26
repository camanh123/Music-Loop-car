package com.musicloop.car.database

import com.musicloop.car.library.MediaListRow
import kotlinx.coroutines.flow.Flow

data class MediaKey(
    val volumeId: String,
    val relativePath: String
)

data class PlaylistRecord(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

interface UserCollectionsRepository {
    suspend fun isFavorite(volumeId: String, relativePath: String): Boolean
    suspend fun setFavorite(row: MediaListRow, favorite: Boolean)
    suspend fun favorites(): List<FavoriteEntity>
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    suspend fun createPlaylist(name: String, nowMs: Long = System.currentTimeMillis()): PlaylistRecord
    suspend fun renamePlaylist(id: Long, name: String, nowMs: Long = System.currentTimeMillis())
    suspend fun removePlaylist(id: Long)
    suspend fun playlists(): List<PlaylistRecord>
    fun observePlaylists(): Flow<List<PlaylistRecord>>
    suspend fun addToPlaylist(playlistId: Long, row: MediaListRow, nowMs: Long = System.currentTimeMillis())
    suspend fun removeFromPlaylist(playlistId: Long, volumeId: String, relativePath: String)
    suspend fun playlistItems(playlistId: Long): List<PlaylistItemEntity>
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>
}
