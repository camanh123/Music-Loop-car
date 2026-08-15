package com.musicloop.car.library

import com.musicloop.car.database.InMemoryLibraryRepository
import com.musicloop.car.database.ScanStatus
import com.musicloop.car.storage.LibraryScanPolicy
import com.musicloop.car.storage.VolumeSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryMediaScannerTest {

    @Test
    fun insertsNewFilesAndBatchesWrites() = runTest {
        val root = createTempDirectory("lib-batch").toFile()
        try {
            repeat(250) { index ->
                root.resolve("track$index.mp3").writeText("data$index")
            }
            val repo = InMemoryLibraryRepository()
            val reader = FakeMetadataReader()
            val scanner = scanner(repo, reader)
            val outcome = scanner.scanVolume(snapshot(root.absolutePath))
            assertEquals(ScanOutcome.COMPLETED, outcome)
            assertEquals(250, repo.mediaForVolume("AAAA-AAAA").size)
            assertTrue(repo.upsertMediaBatchSizes.isNotEmpty())
            assertTrue(repo.upsertMediaBatchSizes.all { it <= LibraryScanPolicy.BATCH_SIZE })
            assertTrue(repo.upsertMediaBatchSizes.any { it == LibraryScanPolicy.BATCH_SIZE })
            assertEquals(250, reader.reads.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unchangedFileIsNotDuplicatedAndSkipsMetadataReread() = runTest {
        val root = createTempDirectory("lib-unchanged").toFile()
        try {
            root.resolve("song.mp3").writeText("same")
            val repo = InMemoryLibraryRepository()
            val reader = FakeMetadataReader()
            val scanner = scanner(repo, reader)
            scanner.scanVolume(snapshot(root.absolutePath))
            reader.reads.clear()
            repo.upsertMediaBatchSizes.clear()
            scanner.scanVolume(snapshot(root.absolutePath))
            val items = repo.mediaForVolume("AAAA-AAAA")
            assertEquals(1, items.size)
            assertEquals(0, reader.reads.size)
            assertEquals("song.mp3", items.single().fileName)
            assertEquals(ScanStatus.COMPLETE, items.single().scanStatus)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun changedFileRereadsMetadata() = runTest {
        val root = createTempDirectory("lib-changed").toFile()
        try {
            val file = root.resolve("song.mp3")
            file.writeText("v1")
            val repo = InMemoryLibraryRepository()
            val reader = FakeMetadataReader()
            val scanner = scanner(repo, reader)
            scanner.scanVolume(snapshot(root.absolutePath))
            file.writeText("v1-changed-bytes")
            reader.reads.clear()
            scanner.scanVolume(snapshot(root.absolutePath))
            assertEquals(listOf("song.mp3"), reader.reads)
            assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
            assertTrue(repo.mediaForVolume("AAAA-AAAA").single().sizeBytes > 2)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun newFileIsInsertedOnRescan() = runTest {
        val root = createTempDirectory("lib-new").toFile()
        try {
            root.resolve("a.mp3").writeText("a")
            val repo = InMemoryLibraryRepository()
            val scanner = scanner(repo, FakeMetadataReader())
            scanner.scanVolume(snapshot(root.absolutePath))
            root.resolve("b.flac").writeText("b")
            scanner.scanVolume(snapshot(root.absolutePath))
            val names = repo.mediaForVolume("AAAA-AAAA").map { it.fileName }.toSet()
            assertEquals(setOf("a.mp3", "b.flac"), names)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingFileIsMarkedStaleNotDeleted() = runTest {
        val root = createTempDirectory("lib-stale").toFile()
        try {
            val gone = root.resolve("gone.mp3")
            gone.writeText("x")
            root.resolve("keep.mp3").writeText("y")
            val repo = InMemoryLibraryRepository()
            val scanner = scanner(repo, FakeMetadataReader())
            scanner.scanVolume(snapshot(root.absolutePath))
            gone.delete()
            scanner.scanVolume(snapshot(root.absolutePath))
            val items = repo.mediaForVolume("AAAA-AAAA").associateBy { it.fileName }
            assertEquals(2, items.size)
            assertEquals(ScanStatus.STALE, items.getValue("gone.mp3").scanStatus)
            assertEquals(ScanStatus.COMPLETE, items.getValue("keep.mp3").scanStatus)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun metadataFailureStillInsertsPartial() = runTest {
        val root = createTempDirectory("lib-partial").toFile()
        try {
            root.resolve("bad.mp3").writeText("x")
            root.resolve("good.mp3").writeText("y")
            val repo = InMemoryLibraryRepository()
            val reader = FakeMetadataReader(failNames = setOf("bad.mp3"), throwNames = setOf("good.mp3"))
            val outcome = scanner(repo, reader).scanVolume(snapshot(root.absolutePath))
            assertEquals(ScanOutcome.COMPLETED, outcome)
            val items = repo.mediaForVolume("AAAA-AAAA").associateBy { it.fileName }
            assertEquals(ScanStatus.PARTIAL, items.getValue("bad.mp3").scanStatus)
            assertEquals(ScanStatus.PARTIAL, items.getValue("good.mp3").scanStatus)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancelledScanDoesNotMarkMissingFilesStale() = runTest {
        val root = createTempDirectory("lib-cancel").toFile()
        try {
            root.resolve("one.mp3").writeText("1")
            root.resolve("two.mp3").writeText("2")
            val repo = InMemoryLibraryRepository()
            val scanner = scanner(repo, FakeMetadataReader())
            scanner.scanVolume(snapshot(root.absolutePath))
            var calls = 0
            val outcome = scanner.scanVolume(
                snapshot = snapshot(root.absolutePath),
                isCancelled = {
                    calls += 1
                    calls > 1
                }
            )
            assertEquals(ScanOutcome.CANCELLED, outcome)
            assertTrue(repo.mediaForVolume("AAAA-AAAA").none { it.scanStatus == ScanStatus.STALE })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun scanner(
        repo: InMemoryLibraryRepository,
        reader: MetadataReader
    ): LibraryMediaScanner {
        return LibraryMediaScanner(
            repository = repo,
            metadataReader = reader,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
    }

    private fun snapshot(rootPath: String): VolumeSnapshot {
        return VolumeSnapshot(
            description = "USB DISK",
            state = "mounted",
            removable = true,
            isPrimary = false,
            uuid = "AAAA-AAAA",
            rootPath = rootPath,
            exists = true,
            isDirectory = true,
            canRead = true,
            listFilesNonNull = true
        )
    }
}
