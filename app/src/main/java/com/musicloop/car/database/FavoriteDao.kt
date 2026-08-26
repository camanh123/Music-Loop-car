package com.musicloop.car.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE volumeId = :volumeId AND relativePath = :relativePath")
    fun remove(volumeId: String, relativePath: String)

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE volumeId = :volumeId AND relativePath = :relativePath")
    fun count(volumeId: String, relativePath: String): Int
}
