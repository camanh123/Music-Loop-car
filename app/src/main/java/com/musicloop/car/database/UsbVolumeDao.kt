package com.musicloop.car.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsbVolumeDao {
    @Query("SELECT * FROM usb_volumes ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<UsbVolumeEntity>>

    @Query("SELECT * FROM usb_volumes ORDER BY lastSeenAt DESC")
    fun getAll(): List<UsbVolumeEntity>

    @Query("SELECT * FROM usb_volumes WHERE volumeId = :volumeId LIMIT 1")
    fun getByVolumeId(volumeId: String): UsbVolumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: UsbVolumeEntity): Long

    @Update
    fun update(entity: UsbVolumeEntity)
}
