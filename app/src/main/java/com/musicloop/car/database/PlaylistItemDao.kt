package com.musicloop.car.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND volumeId = :volumeId AND relativePath = :relativePath")
    fun removeItem(playlistId: Long, volumeId: String, relativePath: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    fun removeForPlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun observeForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items ORDER BY playlistId ASC, position ASC, addedAt ASC")
    fun observeAll(): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun getForPlaylist(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    fun maxPosition(playlistId: Long): Int
}
