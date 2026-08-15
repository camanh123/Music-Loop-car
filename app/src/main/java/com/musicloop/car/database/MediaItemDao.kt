package com.musicloop.car.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY fileName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE volumeId = :volumeId")
    suspend fun getForVolume(volumeId: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Update
    suspend fun updateAll(items: List<MediaItemEntity>)

    @Query(
        "UPDATE media_items SET scanStatus = :status, lastScannedAt = :at WHERE id IN (:ids)"
    )
    suspend fun markStatus(ids: List<Long>, status: String, at: Long)

    @Query("SELECT COUNT(*) FROM media_items WHERE mediaType = :mediaType")
    suspend fun countByType(mediaType: String): Int
}
