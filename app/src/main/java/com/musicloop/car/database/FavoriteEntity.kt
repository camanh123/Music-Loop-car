package com.musicloop.car.database

import androidx.room.Entity

@Entity(
    tableName = "favorites",
    primaryKeys = ["volumeId", "relativePath"]
)
data class FavoriteEntity(
    val volumeId: String,
    val relativePath: String,
    val fileName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val mediaType: String,
    val addedAt: Long
)
