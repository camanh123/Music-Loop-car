package com.musicloop.car.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryTest {

    @Test
    fun insertVolumeAndMedia() = runTest {
        val repo = InMemoryLibraryRepository()
        repo.upsertVolume(volume("AAAA-AAAA", online = true, root = "/mnt/media_rw/AAAA-AAAA"))
        repo.upsertMedia(listOf(media("AAAA-AAAA", "Music/a.mp3")))
        assertEquals("AAAA-AAAA", repo.getVolume("AAAA-AAAA")?.volumeId)
        assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
        assertEquals("Music/a.mp3", repo.mediaForVolume("AAAA-AAAA").single().relativePath)
    }

    @Test
    fun batchInsertPersistsAllItems() = runTest {
        val repo = InMemoryLibraryRepository()
        val items = (1..120).map { index ->
            media("VOL", "f$index.mp3", fileName = "f$index.mp3")
        }
        items.chunked(100).forEach { repo.upsertMedia(it) }
        assertEquals(120, repo.mediaForVolume("VOL").size)
        assertEquals(listOf(100, 20), repo.upsertMediaBatchSizes)
    }

    @Test
    fun updateExistingMediaReplacesMetadataWithoutDuplicate() = runTest {
        val repo = InMemoryLibraryRepository()
        repo.upsertMedia(listOf(media("VOL", "a.mp3", title = "Old", size = 10L, modified = 1L)))
        val existing = repo.mediaForVolume("VOL").single()
        repo.upsertMedia(
            listOf(
                existing.copy(
                    title = "New",
                    sizeBytes = 20L,
                    modifiedTime = 2L
                )
            )
        )
        val items = repo.mediaForVolume("VOL")
        assertEquals(1, items.size)
        assertEquals("New", items.single().title)
        assertEquals(20L, items.single().sizeBytes)
        assertEquals(existing.id, items.single().id)
    }

    @Test
    fun offlineVolumeRetainsMediaRecords() = runTest {
        val repo = InMemoryLibraryRepository()
        repo.upsertVolume(volume("AAAA-AAAA", online = true, root = "/mnt/one"))
        repo.upsertMedia(listOf(media("AAAA-AAAA", "clip.mp4")))
        repo.upsertVolume(volume("AAAA-AAAA", online = false, root = "/mnt/one"))
        val stored = repo.getVolume("AAAA-AAAA")!!
        assertFalse(stored.isOnline)
        assertEquals("/mnt/one", stored.lastKnownRootPath)
        assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
    }

    @Test
    fun remountRestoresOnlineFlagAndKeepsIdentityWhenRootChanges() = runTest {
        val repo = InMemoryLibraryRepository()
        repo.upsertVolume(volume("AAAA-AAAA", online = true, root = "/mnt/one", seenAt = 10L))
        repo.upsertMedia(listOf(media("AAAA-AAAA", "song.mp3")))
        repo.upsertVolume(volume("AAAA-AAAA", online = false, root = "/mnt/one", seenAt = 20L))
        repo.upsertVolume(volume("AAAA-AAAA", online = true, root = "/mnt/two", seenAt = 30L))
        val stored = repo.getVolume("AAAA-AAAA")!!
        assertTrue(stored.isOnline)
        assertEquals("/mnt/two", stored.lastKnownRootPath)
        assertEquals("AAAA-AAAA", stored.volumeId)
        assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
        assertEquals(10L, stored.createdAt)
    }

    private fun volume(
        volumeId: String,
        online: Boolean,
        root: String?,
        seenAt: Long = 1L
    ): UsbVolumeEntity {
        return UsbVolumeEntity(
            volumeId = volumeId,
            description = "USB DISK",
            uuid = volumeId,
            lastKnownRootPath = root,
            isOnline = online,
            lastSeenAt = seenAt,
            createdAt = 10L,
            updatedAt = seenAt
        )
    }

    private fun media(
        volumeId: String,
        relativePath: String,
        fileName: String = relativePath.substringAfterLast('/'),
        title: String? = null,
        size: Long = 1L,
        modified: Long = 1L
    ): MediaItemEntity {
        return MediaItemEntity(
            volumeId = volumeId,
            relativePath = relativePath,
            fileName = fileName,
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = size,
            modifiedTime = modified,
            durationMs = 1000L,
            title = title,
            artist = null,
            album = null,
            width = null,
            height = null,
            scanStatus = ScanStatus.COMPLETE,
            lastScannedAt = 1L
        )
    }
}
