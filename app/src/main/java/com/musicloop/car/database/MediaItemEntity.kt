package com.musicloop.car.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["volumeId", "relativePath"], unique = true),
        Index(value = ["volumeId"]),
        Index(value = ["mediaType"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeId: String,
    val relativePath: String,
    val fileName: String,
    val extension: String,
    val mediaType: String,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val durationMs: Long?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val width: Int?,
    val height: Int?,
    val scanStatus: String,
    val lastScannedAt: Long
)
