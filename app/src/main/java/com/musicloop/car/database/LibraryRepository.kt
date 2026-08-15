package com.musicloop.car.database

import kotlinx.coroutines.flow.Flow

data class LibrarySnapshot(
    val volumes: List<UsbVolumeEntity>,
    val media: List<MediaItemEntity>
) {
    val audioCount: Int get() = media.count { it.mediaType == "AUDIO" }
    val videoCount: Int get() = media.count { it.mediaType == "VIDEO" }
    val totalCount: Int get() = media.size
}

interface LibraryRepository {
    suspend fun getAllVolumes(): List<UsbVolumeEntity>
    suspend fun getVolume(volumeId: String): UsbVolumeEntity?
    suspend fun upsertVolume(entity: UsbVolumeEntity)
    suspend fun mediaForVolume(volumeId: String): List<MediaItemEntity>
    suspend fun upsertMedia(items: List<MediaItemEntity>)
    suspend fun markStale(ids: List<Long>, at: Long)
    fun observeLibrary(): Flow<LibrarySnapshot>
}
