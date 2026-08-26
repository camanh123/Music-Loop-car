package com.musicloop.car.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert
    fun insert(entity: PlaylistEntity): Long

    @Update
    fun update(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    fun remove(id: Long)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getById(id: Long): PlaylistEntity?
}
