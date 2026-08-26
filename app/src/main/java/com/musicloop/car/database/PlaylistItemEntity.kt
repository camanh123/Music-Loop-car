package com.musicloop.car.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "volumeId", "relativePath"],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val position: Int,
    val volumeId: String,
    val relativePath: String,
    val fileName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val mediaType: String,
    val addedAt: Long
)
