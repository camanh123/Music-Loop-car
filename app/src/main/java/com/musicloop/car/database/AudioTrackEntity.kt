package com.musicloop.car.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_tracks",
    indices = [
        Index(value = ["volume_identity", "relative_path"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["filename"])
    ]
)
data class AudioTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "volume_identity") val volumeIdentity: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "absolute_path") val absolutePath: String,
    val filename: String,
    val extension: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_artist") val albumArtist: String,
    val genre: String,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "artwork_key") val artworkKey: String?,
    @ColumnInfo(name = "scan_state") val scanState: String,
    @ColumnInfo(name = "metadata_state") val metadataState: String,
    @ColumnInfo(name = "playable_state") val playableState: String,
    val favorite: Boolean,
    @ColumnInfo(name = "verify_failures") val verifyFailures: Int,
    @ColumnInfo(name = "last_verified_at") val lastVerifiedAt: Long,
    @ColumnInfo(name = "missing_confirmed") val missingConfirmed: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "volume_identity") val volumeIdentity: String?,
    val phase: String,
    val processed: Int,
    val total: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
