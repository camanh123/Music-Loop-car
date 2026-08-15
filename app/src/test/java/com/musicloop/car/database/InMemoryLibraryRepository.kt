package com.musicloop.car.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class InMemoryLibraryRepository : LibraryRepository {
    private var volumeSeq = 1L
    private var mediaSeq = 1L
    private val volumes = LinkedHashMap<String, UsbVolumeEntity>()
    private val media = LinkedHashMap<Pair<String, String>, MediaItemEntity>()
    private val snapshot = MutableStateFlow(LibrarySnapshot(emptyList(), emptyList()))

    var upsertMediaBatchSizes: MutableList<Int> = mutableListOf()

    override suspend fun getAllVolumes(): List<UsbVolumeEntity> = volumes.values.toList()

    override suspend fun getVolume(volumeId: String): UsbVolumeEntity? = volumes[volumeId]

    override suspend fun upsertVolume(entity: UsbVolumeEntity) {
        val existing = volumes[entity.volumeId]
        val stored = if (existing == null) {
            entity.copy(id = if (entity.id != 0L) entity.id else volumeSeq++)
        } else {
            existing.copy(
                description = entity.description.ifBlank { existing.description },
                uuid = entity.uuid ?: existing.uuid,
                lastKnownRootPath = entity.lastKnownRootPath ?: existing.lastKnownRootPath,
                isOnline = entity.isOnline,
                lastSeenAt = if (entity.isOnline) entity.lastSeenAt else existing.lastSeenAt,
                updatedAt = entity.updatedAt
            )
        }
        volumes[stored.volumeId] = stored
        publish()
    }

    override suspend fun mediaForVolume(volumeId: String): List<MediaItemEntity> {
        return media.values.filter { it.volumeId == volumeId }
    }

    override suspend fun upsertMedia(items: List<MediaItemEntity>) {
        if (items.isEmpty()) {
            return
        }
        upsertMediaBatchSizes += items.size
        for (item in items) {
            val key = item.volumeId to item.relativePath
            val existing = media[key]
            val stored = if (existing == null) {
                item.copy(id = if (item.id != 0L) item.id else mediaSeq++)
            } else {
                item.copy(id = existing.id)
            }
            media[key] = stored
        }
        publish()
    }

    override suspend fun markStale(ids: List<Long>, at: Long) {
        if (ids.isEmpty()) {
            return
        }
        val idSet = ids.toSet()
        val updated = media.mapValues { (_, item) ->
            if (item.id in idSet) {
                item.copy(scanStatus = ScanStatus.STALE, lastScannedAt = at)
            } else {
                item
            }
        }
        media.clear()
        media.putAll(updated)
        publish()
    }

    override fun observeLibrary(): Flow<LibrarySnapshot> = snapshot

    private fun publish() {
        snapshot.value = LibrarySnapshot(
            volumes = volumes.values.toList(),
            media = media.values.sortedBy { it.fileName.lowercase(Locale.US) }
        )
    }
}
