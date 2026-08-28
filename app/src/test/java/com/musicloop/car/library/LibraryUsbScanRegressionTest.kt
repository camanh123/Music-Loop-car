package com.musicloop.car.library

import com.musicloop.car.database.InMemoryLibraryRepository
import com.musicloop.car.database.ScanStatus
import com.musicloop.car.storage.VolumeSnapshot
import com.musicloop.car.usb.UsbLifecycleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryUsbScanRegressionTest {

    @Test
    fun nestedCaseInsensitiveMp3Mp4PopulateRoomAndLibraryQuery() = runTest {
        val root = createTempDirectory("scan-tree").toFile()
        try {
            root.resolve("Music").mkdirs()
            root.resolve("Music/a.mp3").writeText("a")
            root.resolve("Music/B.MP3").writeText("b")
            root.resolve("Videos").mkdirs()
            root.resolve("Videos/c.mp4").writeText("c")
            root.resolve("Videos/D.MP4").writeText("d")
            val repo = InMemoryLibraryRepository()
            assertTrue(repo.mediaForVolume("AAAA-AAAA").isEmpty())
            val outcome = scanner(repo).scanVolume(snapshot(root.absolutePath, canWrite = false))
            assertEquals(ScanOutcome.COMPLETED, outcome)
            val items = repo.mediaForVolume("AAAA-AAAA")
            assertEquals(2, items.count { it.mediaType == "AUDIO" })
            assertEquals(2, items.count { it.mediaType == "VIDEO" })
            val rows = items.map { it.toMediaListRow() }
            assertEquals(2, LibraryListQuery.apply(rows, LibraryTab.MUSIC, "", LibrarySort.A_Z).size)
            assertEquals(2, LibraryListQuery.apply(rows, LibraryTab.VIDEO, "", LibrarySort.A_Z).size)
            assertTrue(items.any { it.relativePath == "Music/a.mp3" })
            assertTrue(items.any { it.relativePath == "Videos/D.MP4" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun metadataFailureKeepsValidMp3AndMp4() = runTest {
        val root = createTempDirectory("scan-partial").toFile()
        try {
            root.resolve("bad.mp3").writeText("corrupt")
            root.resolve("good.mp3").writeText("ok")
            root.resolve("clip.mp4").writeText("vid")
            val repo = InMemoryLibraryRepository()
            val reader = FakeMetadataReader(throwNames = setOf("bad.mp3"))
            val outcome = scanner(repo, reader).scanVolume(snapshot(root.absolutePath))
            assertEquals(ScanOutcome.COMPLETED, outcome)
            val items = repo.mediaForVolume("AAAA-AAAA").associateBy { it.fileName }
            assertEquals(3, items.size)
            assertEquals(ScanStatus.PARTIAL, items.getValue("bad.mp3").scanStatus)
            assertEquals("AUDIO", items.getValue("good.mp3").mediaType)
            assertEquals("VIDEO", items.getValue("clip.mp4").mediaType)
            assertEquals("good", items.getValue("good.mp3").title)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun emptyRoomReadableUsbScanRefreshesUiRows() = runTest {
        val root = createTempDirectory("scan-empty-room").toFile()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        try {
            root.resolve("Music").mkdirs()
            root.resolve("Music/a.mp3").writeText("a")
            root.resolve("Videos").mkdirs()
            root.resolve("Videos/c.mp4").writeText("c")
            val repo = InMemoryLibraryRepository()
            assertTrue(repo.getAllVolumes().isEmpty())
            val snapshots = mutableListOf(snapshot(root.absolutePath).copy(canWrite = false, canRead = false))
            val controller = UsbLifecycleController(
                snapshotVolumes = { snapshots.toList() },
                scanner = scanner(repo),
                repository = repo,
                scope = scope,
                now = { 1_000L }
            )
            controller.start()
            advanceUntilIdle()
            val media = controller.uiState.value.media
            assertEquals(ScanUiState.COMPLETED, controller.uiState.value.scanState)
            assertEquals(1, media.count { it.mediaType == "AUDIO" })
            assertEquals(1, media.count { it.mediaType == "VIDEO" })
            assertTrue(media.any { it.fileName == "a.mp3" })
            assertTrue(media.any { it.fileName == "c.mp4" })
            assertFalse(UsbAccessAllowsDelete(snapshots.single()))
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private fun UsbAccessAllowsDelete(snapshot: VolumeSnapshot): Boolean {
        return com.musicloop.car.storage.UsbAccess.classify(snapshot).allowsDelete
    }

    private fun scanner(
        repo: InMemoryLibraryRepository,
        reader: MetadataReader = FakeMetadataReader()
    ): LibraryMediaScanner {
        return LibraryMediaScanner(
            repository = repo,
            metadataReader = reader,
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    private fun snapshot(rootPath: String, canWrite: Boolean = false): VolumeSnapshot {
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
            listFilesNonNull = true,
            canWrite = canWrite
        )
    }
}
