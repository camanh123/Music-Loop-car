package com.musicloop.car.library

import com.musicloop.car.database.InMemoryLibraryRepository
import com.musicloop.car.database.InMemoryUserCollectionsRepository
import com.musicloop.car.database.MediaItemEntity
import com.musicloop.car.database.ScanStatus
import com.musicloop.car.playback.DeletionPlaybackGate
import com.musicloop.car.playback.MediaItemResolver
import com.musicloop.car.storage.JavaUsbFileSystem
import com.musicloop.car.storage.UsbFileSystem
import com.musicloop.car.storage.VolumeSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SafeUsbDeletionCoordinatorTest {

    @Test
    fun deleteIdentityResolvesFromCurrentVolume() = runTest {
        val root = tempRoot()
        val file = mediaFile(root, "song.mp3")
        val repo = repoWith(audio("song.mp3"))
        val fs = JavaUsbFileSystem()
        val gate = RecordingGate()
        val coordinator = coordinator(
            root = root.absolutePath,
            repo = repo,
            fs = fs,
            gate = gate
        )
        val result = coordinator.deleteAll(listOf(id("song.mp3")))
        assertEquals(1, result.succeeded.size)
        assertFalse(file.exists())
        assertTrue(repo.mediaForVolume(VOLUME).isEmpty())
    }

    @Test
    fun staleAbsolutePathIsNeverTrusted() = runTest {
        val currentRoot = "/mnt/media_rw/new-root"
        val staleRoot = "/mnt/media_rw/old-root"
        val fs = FakeFs()
        fs.add("$currentRoot/song.mp3")
        fs.add("$staleRoot/song.mp3")
        val repo = repoWith(audio("song.mp3"))
        val coordinator = coordinator(
            root = currentRoot,
            repo = repo,
            fs = fs,
            gate = RecordingGate()
        )
        val result = coordinator.deleteAll(listOf(id("song.mp3")))
        assertEquals(listOf("$currentRoot/song.mp3"), fs.deleteCalls)
        assertFalse(fs.exists("$currentRoot/song.mp3"))
        assertTrue(fs.exists("$staleRoot/song.mp3"))
        assertEquals(1, result.succeeded.size)
        assertTrue(coordinatorSourceDoesNotMentionLastKnownRoot())
    }

    @Test
    fun offlineUsbBlocksDelete() = runTest {
        val fs = FakeFs().also { it.add("/mnt/a/song.mp3") }
        val repo = repoWith(audio("song.mp3"))
        val coordinator = coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate(),
            snapshots = { emptyList() }
        )
        val result = coordinator.deleteAll(listOf(id("song.mp3")))
        assertTrue(result.succeeded.isEmpty())
        assertEquals(DeleteFailureReason.OFFLINE, result.failed.single().reason)
        assertTrue(fs.exists("/mnt/a/song.mp3"))
        assertEquals(1, repo.mediaForVolume(VOLUME).size)
        assertTrue(fs.deleteCalls.isEmpty())
    }

    @Test
    fun readOnlyUsbBlocksDelete() = runTest {
        val fs = FakeFs().also {
            it.add("/mnt/a/song.mp3")
            it.writable = false
        }
        val repo = repoWith(audio("song.mp3"))
        val coordinator = coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate(),
            canWrite = true
        )
        val result = coordinator.deleteAll(listOf(id("song.mp3")))
        assertEquals(DeleteFailureReason.READ_ONLY, result.failed.single().reason)
        assertEquals(1, repo.mediaForVolume(VOLUME).size)
        assertTrue(fs.deleteCalls.isEmpty())
        assertTrue(fs.canWriteCalls.isNotEmpty())
    }

    @Test
    fun snapshotCanWriteFalseDoesNotBlockDeleteWhenFsWritable() = runTest {
        val fs = FakeFs().also { it.add("/mnt/a/song.mp3") }
        val repo = repoWith(audio("song.mp3"))
        val result = coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate(),
            canWrite = false
        ).deleteAll(listOf(id("song.mp3")))
        assertEquals(1, result.succeeded.size)
        assertTrue(fs.deleteCalls.contains("/mnt/a/song.mp3"))
        assertTrue(repo.mediaForVolume(VOLUME).isEmpty())
    }

    @Test
    fun writeProbeThrowBlocksDeleteWithoutTouchingFile() = runTest {
        val fs = FakeFs().also {
            it.add("/mnt/a/song.mp3")
            it.throwOnCanWrite = true
        }
        val repo = repoWith(audio("song.mp3"))
        val result = coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate()
        ).deleteAll(listOf(id("song.mp3")))
        assertEquals(DeleteFailureReason.READ_ONLY, result.failed.single().reason)
        assertTrue(fs.exists("/mnt/a/song.mp3"))
        assertTrue(fs.deleteCalls.isEmpty())
        assertEquals(1, repo.mediaForVolume(VOLUME).size)
    }

    @Test
    fun successfulSingleAudioDeletion() = runTest {
        val root = tempRoot()
        val file = mediaFile(root, "track.mp3")
        val repo = repoWith(audio("track.mp3"))
        val result = coordinator(
            root = root.absolutePath,
            repo = repo,
            fs = JavaUsbFileSystem(),
            gate = RecordingGate()
        ).deleteAll(listOf(id("track.mp3")))
        assertEquals(1, result.succeeded.size)
        assertTrue(result.failed.isEmpty())
        assertFalse(file.exists())
        assertTrue(repo.mediaForVolume(VOLUME).isEmpty())
    }

    @Test
    fun successfulSingleVideoDeletion() = runTest {
        val root = tempRoot()
        val file = mediaFile(root, "clip.mp4")
        val repo = repoWith(video("clip.mp4"))
        val result = coordinator(
            root = root.absolutePath,
            repo = repo,
            fs = JavaUsbFileSystem(),
            gate = RecordingGate()
        ).deleteAll(listOf(id("clip.mp4")))
        assertEquals(1, result.succeeded.size)
        assertFalse(file.exists())
        assertTrue(repo.mediaForVolume(VOLUME).isEmpty())
    }

    @Test
    fun failedFilesystemDeleteDoesNotRemoveRoomItem() = runTest {
        val fs = FakeFs().also {
            it.add("/mnt/a/song.mp3")
            it.failDeletes += "/mnt/a/song.mp3"
        }
        val repo = repoWith(audio("song.mp3"))
        val result = coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate()
        ).deleteAll(listOf(id("song.mp3")))
        assertEquals(DeleteFailureReason.FILESYSTEM_REFUSED, result.failed.single().reason)
        assertEquals(1, repo.mediaForVolume(VOLUME).size)
        assertTrue(fs.exists("/mnt/a/song.mp3"))
    }

    @Test
    fun currentlyPlayingAudioStopsBeforeDeletion() = runTest {
        val timeline = mutableListOf<String>()
        val fs = FakeFs(timeline).also { it.add("/mnt/a/song.mp3") }
        val repo = repoWith(audio("song.mp3"))
        val gate = RecordingGate(timeline = timeline, current = id("song.mp3"))
        coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = gate
        ).deleteAll(listOf(id("song.mp3")))
        assertEquals(
            listOf("release:song.mp3", "delete:/mnt/a/song.mp3", "reconcile:song.mp3"),
            timeline
        )
    }

    @Test
    fun videoActiveUseReleasesBeforeDelete() = runTest {
        val timeline = mutableListOf<String>()
        val fs = FakeFs(timeline).also { it.add("/mnt/a/clip.mp4") }
        val repo = repoWith(video("clip.mp4"))
        val gate = RecordingGate(timeline = timeline, videoAttached = id("clip.mp4"))
        coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = gate
        ).deleteAll(listOf(id("clip.mp4")))
        assertEquals(
            listOf("release:clip.mp4", "delete:/mnt/a/clip.mp4", "reconcile:clip.mp4"),
            timeline
        )
    }

    @Test
    fun explicitQueueRemovesDeletedIdentity() = runTest {
        val fs = FakeFs().also { it.add("/mnt/a/song.mp3") }
        val repo = repoWith(audio("song.mp3"))
        val gate = RecordingGate()
        coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = gate
        ).deleteAll(listOf(id("song.mp3")))
        assertEquals(listOf(id("song.mp3")), gate.reconciled)
    }

    @Test
    fun favoriteHistorySemanticsPreserved() = runTest {
        val fs = FakeFs().also { it.add("/mnt/a/song.mp3") }
        val repo = repoWith(audio("song.mp3"))
        val collections = InMemoryUserCollectionsRepository()
        val row = audio("song.mp3").toRow()
        collections.setFavorite(row, true)
        coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate()
        ).deleteAll(listOf(id("song.mp3")))
        assertTrue(collections.isFavorite(VOLUME, "song.mp3"))
        val favorites = collections.favorites()
        assertEquals(1, favorites.size)
        val rendered = CollectionRows.fromFavorites(
            favorites = favorites,
            library = emptyList(),
            usbOnline = true,
            query = "",
            sort = LibrarySort.A_Z
        )
        assertEquals(1, rendered.size)
        assertFalse(rendered.single().available)
        assertEquals("song.mp3", rendered.single().relativePath)
        assertTrue(repo.mediaForVolume(VOLUME).isEmpty())
    }

    @Test
    fun playlistHistorySemanticsPreserved() = runTest {
        val fs = FakeFs().also { it.add("/mnt/a/song.mp3") }
        val repo = repoWith(audio("song.mp3"))
        val collections = InMemoryUserCollectionsRepository()
        val playlist = collections.createPlaylist("Drive")
        collections.addToPlaylist(playlist.id, audio("song.mp3").toRow())
        coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate()
        ).deleteAll(listOf(id("song.mp3")))
        val items = collections.playlistItems(playlist.id)
        assertEquals(1, items.size)
        val rendered = CollectionRows.fromPlaylistItems(items, emptyList(), usbOnline = true, query = "")
        assertFalse(rendered.single().available)
        assertEquals("song.mp3", rendered.single().relativePath)
    }

    @Test
    fun duplicateDeleteRequestBlocked() = runTest {
        val inner = FakeFs().also {
            it.add("/mnt/a/a.mp3")
            it.add("/mnt/a/b.mp3")
        }
        val repo = repoWith(audio("a.mp3"), audio("b.mp3"))
        lateinit var coordinator: SafeUsbDeletionCoordinator
        var nested: BatchDeleteResult? = null
        val fs = object : UsbFileSystem by inner {
            override fun deleteRegularFile(absolutePath: String): Boolean {
                if (nested == null) {
                    nested = kotlinx.coroutines.runBlocking {
                        coordinator.deleteAll(listOf(id("b.mp3")))
                    }
                }
                return inner.deleteRegularFile(absolutePath)
            }
        }
        coordinator = coordinator(root = "/mnt/a", repo = repo, fs = fs, gate = RecordingGate())
        val result = coordinator.deleteAll(listOf(id("a.mp3"), id("b.mp3")))
        assertTrue(nested!!.duplicateBlocked)
        assertEquals(DeleteFailureReason.DUPLICATE_IN_PROGRESS, nested!!.failed.single().reason)
        assertEquals(2, result.succeeded.size)
    }

    @Test
    fun batchDeleteFullSuccess() = runTest {
        val fs = FakeFs().also {
            it.add("/mnt/a/a.mp3")
            it.add("/mnt/a/b.mp3")
            it.add("/mnt/a/c.mp4")
        }
        val repo = repoWith(audio("a.mp3"), audio("b.mp3"), video("c.mp4"))
        val result = coordinator(root = "/mnt/a", repo = repo, fs = fs, gate = RecordingGate())
            .deleteAll(listOf(id("a.mp3"), id("b.mp3"), id("c.mp4")))
        assertEquals(3, result.succeeded.size)
        assertTrue(result.failed.isEmpty())
        assertTrue(repo.mediaForVolume(VOLUME).isEmpty())
    }

    @Test
    fun batchDeletePartialFailure() = runTest {
        val fs = FakeFs().also {
            it.add("/mnt/a/a.mp3")
            it.add("/mnt/a/b.mp3")
            it.failDeletes += "/mnt/a/b.mp3"
        }
        val repo = repoWith(audio("a.mp3"), audio("b.mp3"))
        val result = coordinator(root = "/mnt/a", repo = repo, fs = fs, gate = RecordingGate())
            .deleteAll(listOf(id("a.mp3"), id("b.mp3")))
        assertEquals(1, result.succeeded.size)
        assertEquals(1, result.failed.size)
        assertEquals("a.mp3", result.succeeded.single().relativePath)
        assertEquals("b.mp3", repo.mediaForVolume(VOLUME).single().relativePath)
        assertFalse(fs.exists("/mnt/a/a.mp3"))
        assertTrue(fs.exists("/mnt/a/b.mp3"))
    }

    @Test
    fun usbDisconnectDuringBatch() = runTest {
        val files = mutableSetOf("/mnt/a/a.mp3", "/mnt/a/b.mp3")
        var present = true
        val fs = object : UsbFileSystem {
            override fun exists(absolutePath: String) = absolutePath in files
            override fun isRegularFile(absolutePath: String) = absolutePath in files
            override fun canWrite(absolutePath: String): Boolean {
                return files.any { it == absolutePath || it.startsWith("$absolutePath/") }
            }
            override fun canonicalPath(absolutePath: String) = absolutePath
            override fun deleteRegularFile(absolutePath: String): Boolean {
                val ok = files.remove(absolutePath)
                present = false
                return ok
            }
        }
        val repo = repoWith(audio("a.mp3"), audio("b.mp3"))
        val result = coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate(),
            snapshots = {
                if (present) listOf(onlineSnapshot("/mnt/a", canWrite = true)) else emptyList()
            }
        ).deleteAll(listOf(id("a.mp3"), id("b.mp3")))
        assertEquals(1, result.succeeded.size)
        assertEquals("a.mp3", result.succeeded.single().relativePath)
        assertEquals(DeleteFailureReason.OFFLINE, result.failed.single().reason)
        assertEquals("b.mp3", repo.mediaForVolume(VOLUME).single().relativePath)
        assertFalse("/mnt/a/a.mp3" in files)
        assertTrue("/mnt/a/b.mp3" in files)
    }

    @Test
    fun pathEscapeIsRejected() = runTest {
        val fs = FakeFs().also { it.add("/mnt/other/secret.mp3") }
        val repo = repoWith(audio("../other/secret.mp3"))
        val result = coordinator(
            root = "/mnt/a",
            repo = repo,
            fs = fs,
            gate = RecordingGate()
        ).deleteAll(listOf(id("../other/secret.mp3")))
        assertEquals(DeleteFailureReason.PATH_ESCAPE, result.failed.single().reason)
        assertTrue(fs.deleteCalls.isEmpty())
        assertEquals(1, repo.mediaForVolume(VOLUME).size)
    }

    private fun coordinator(
        root: String,
        repo: InMemoryLibraryRepository,
        fs: UsbFileSystem,
        gate: DeletionPlaybackGate,
        canWrite: Boolean = true,
        snapshots: () -> List<VolumeSnapshot> = { listOf(onlineSnapshot(root, canWrite)) }
    ): SafeUsbDeletionCoordinator {
        val resolver = MediaItemResolver(
            snapshotVolumes = snapshots,
            fileReadable = { file -> fs.exists(file.absolutePath) && fs.isRegularFile(file.absolutePath) }
        )
        return SafeUsbDeletionCoordinator(
            snapshotVolumes = snapshots,
            libraryLookup = { volumeId, relativePath -> repo.mediaByIdentity(volumeId, relativePath) },
            removeFromLibrary = { volumeId, relativePath -> repo.removeMedia(volumeId, relativePath) },
            playback = gate,
            fs = fs,
            resolver = resolver
        )
    }

    private suspend fun repoWith(vararg items: MediaItemEntity): InMemoryLibraryRepository {
        val repo = InMemoryLibraryRepository()
        repo.upsertMedia(items.toList())
        return repo
    }

    private fun audio(relativePath: String): MediaItemEntity {
        return MediaItemEntity(
            volumeId = VOLUME,
            relativePath = relativePath,
            fileName = relativePath.substringAfterLast('/'),
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = 10L,
            modifiedTime = 1L,
            durationMs = 1000L,
            title = relativePath,
            artist = "Artist",
            album = null,
            width = null,
            height = null,
            scanStatus = ScanStatus.COMPLETE,
            lastScannedAt = 1L
        )
    }

    private fun video(relativePath: String): MediaItemEntity {
        return audio(relativePath).copy(
            extension = "mp4",
            mediaType = "VIDEO",
            fileName = relativePath.substringAfterLast('/')
        )
    }

    private fun MediaItemEntity.toRow(): MediaListRow {
        return MediaListRow(
            id = id,
            volumeId = volumeId,
            relativePath = relativePath,
            fileName = fileName,
            extension = extension,
            mediaType = mediaType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album,
            scanStatus = scanStatus
        )
    }

    private fun id(relativePath: String) = MediaIdentity(VOLUME, relativePath)

    private fun tempRoot(): File {
        return File.createTempFile("usb-del", "dir").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
    }

    private fun mediaFile(root: File, name: String): File {
        val file = File(root, name)
        file.writeText("media")
        return file
    }

    private fun coordinatorSourceDoesNotMentionLastKnownRoot(): Boolean {
        val roots = listOf(
            File("src/main/java/com/musicloop/car/library/SafeUsbDeletionCoordinator.kt"),
            File("../app/src/main/java/com/musicloop/car/library/SafeUsbDeletionCoordinator.kt")
        )
        val text = roots.first { it.isFile }.readText()
            .lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
            }
            .joinToString("\n")
        return !text.contains("lastKnownRootPath")
    }

    private class RecordingGate(
        private val timeline: MutableList<String> = mutableListOf(),
        var current: MediaIdentity? = null,
        var videoAttached: MediaIdentity? = null
    ) : DeletionPlaybackGate {
        val events = mutableListOf<String>()
        var reconciled: List<MediaIdentity> = emptyList()

        override fun isCurrent(identity: MediaIdentity): Boolean = current == identity
        override fun isVideoAttached(identity: MediaIdentity): Boolean = videoAttached == identity
        override fun releaseForDeletion(identity: MediaIdentity) {
            val event = "release:${identity.relativePath}"
            events += event
            timeline += event
        }
        override fun reconcileDeleted(identities: List<MediaIdentity>) {
            reconciled = identities
            val event = "reconcile:${identities.joinToString { it.relativePath }}"
            events += event
            timeline += event
        }
    }

    private class FakeFs(
        private val timeline: MutableList<String> = mutableListOf()
    ) : UsbFileSystem {
        private val files = mutableSetOf<String>()
        val failDeletes = mutableSetOf<String>()
        val deleteCalls = mutableListOf<String>()
        val events = mutableListOf<String>()

        var writable: Boolean = true
        var throwOnCanWrite: Boolean = false
        val canWriteCalls = mutableListOf<String>()

        fun add(path: String) {
            files += path
        }

        override fun exists(absolutePath: String): Boolean = absolutePath in files
        override fun isRegularFile(absolutePath: String): Boolean = absolutePath in files
        override fun canWrite(absolutePath: String): Boolean {
            canWriteCalls += absolutePath
            if (throwOnCanWrite) {
                throw IllegalStateException("write probe failed")
            }
            if (!writable) {
                return false
            }
            return absolutePath in files || files.any { it.startsWith("$absolutePath/") }
        }
        override fun canonicalPath(absolutePath: String): String? = absolutePath
        override fun deleteRegularFile(absolutePath: String): Boolean {
            deleteCalls += absolutePath
            val event = "delete:$absolutePath"
            events += event
            timeline += event
            if (absolutePath in failDeletes) {
                return false
            }
            return files.remove(absolutePath)
        }
    }

    companion object {
        private const val VOLUME = "AAAA-AAAA"

        fun onlineSnapshot(root: String, canWrite: Boolean): VolumeSnapshot {
            return VolumeSnapshot(
                description = "USB DISK",
                state = "mounted",
                removable = true,
                isPrimary = false,
                uuid = VOLUME,
                rootPath = root,
                exists = true,
                isDirectory = true,
                canRead = true,
                listFilesNonNull = true,
                canWrite = canWrite
            )
        }
    }
}
