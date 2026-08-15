package com.musicloop.car.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usb_volumes",
    indices = [Index(value = ["volumeId"], unique = true)]
)
data class UsbVolumeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeId: String,
    val description: String,
    val uuid: String?,
    val lastKnownRootPath: String?,
    val isOnline: Boolean,
    val lastSeenAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)
