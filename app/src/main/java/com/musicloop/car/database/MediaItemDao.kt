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
    fun getForVolume(volumeId: String): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<MediaItemEntity>)

    @Update
    fun updateAll(items: List<MediaItemEntity>)

    @Query(
        "UPDATE media_items SET scanStatus = :status, lastScannedAt = :at WHERE id IN (:ids)"
    )
    fun markStatus(ids: List<Long>, status: String, at: Long)
}
