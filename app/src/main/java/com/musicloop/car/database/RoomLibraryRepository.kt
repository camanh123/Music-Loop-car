package com.musicloop.car.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomLibraryRepository(
    database: AppDatabase
) : LibraryRepository {
    private val volumes = database.usbVolumeDao()
    private val media = database.mediaItemDao()

    override suspend fun getAllVolumes(): List<UsbVolumeEntity> = volumes.getAll()

    override suspend fun getVolume(volumeId: String): UsbVolumeEntity? =
        volumes.getByVolumeId(volumeId)

    override suspend fun upsertVolume(entity: UsbVolumeEntity) {
        val existing = volumes.getByVolumeId(entity.volumeId)
        if (existing == null) {
            volumes.insert(entity)
            return
        }
        volumes.update(
            existing.copy(
                description = entity.description.ifBlank { existing.description },
                uuid = entity.uuid ?: existing.uuid,
                lastKnownRootPath = entity.lastKnownRootPath ?: existing.lastKnownRootPath,
                isOnline = entity.isOnline,
                lastSeenAt = if (entity.isOnline) entity.lastSeenAt else existing.lastSeenAt,
                updatedAt = entity.updatedAt
            )
        )
    }

    override suspend fun mediaForVolume(volumeId: String): List<MediaItemEntity> =
        media.getForVolume(volumeId)

    override suspend fun upsertMedia(items: List<MediaItemEntity>) {
        if (items.isEmpty()) {
            return
        }
        val inserts = items.filter { it.id == 0L }
        val updates = items.filter { it.id != 0L }
        if (inserts.isNotEmpty()) {
            media.insertAll(inserts)
        }
        if (updates.isNotEmpty()) {
            media.updateAll(updates)
        }
    }

    override suspend fun markStale(ids: List<Long>, at: Long) {
        if (ids.isEmpty()) {
            return
        }
        ids.chunked(STATUS_BATCH).forEach { chunk ->
            media.markStatus(chunk, ScanStatus.STALE, at)
        }
    }

    override fun observeLibrary(): Flow<LibrarySnapshot> {
        return combine(volumes.observeAll(), media.observeAll()) { volumeRows, mediaRows ->
            LibrarySnapshot(volumeRows, mediaRows)
        }
    }

    companion object {
        private const val STATUS_BATCH = 100
    }
}
