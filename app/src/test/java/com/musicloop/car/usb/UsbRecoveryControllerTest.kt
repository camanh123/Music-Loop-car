package com.musicloop.car.usb

import android.content.Intent
import com.musicloop.car.database.InMemoryLibraryRepository
import com.musicloop.car.library.FakeMetadataReader
import com.musicloop.car.library.LibraryMediaScanner
import com.musicloop.car.library.ScanUiState
import com.musicloop.car.storage.VolumeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class UsbRecoveryControllerTest {

    @Test
    fun onlineThenOfflineUsesStorageManagerNotBroadcastAlone() = runTest {
        val root = createTempDirectory("rec-off").toFile()
        val scope = testScope()
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(root.absolutePath))
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            assertTrue(controller.uiState.value.usbOnline)
            snapshots.clear()
            controller.onBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
            advanceUntilIdle()
            assertFalse(controller.uiState.value.usbOnline)
            assertEquals(UsbHostState.USB_OFFLINE, controller.uiState.value.usbHostState)
            assertEquals(ScanUiState.USB_OFFLINE, controller.uiState.value.scanState)
            assertTrue(controller.uiState.value.diagnosticMessage!!.contains("USB chưa được Android nhận diện"))
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun unmountBroadcastIsIgnoredWhenStorageManagerStillHasVolume() = runTest {
        val root = createTempDirectory("rec-ignore").toFile()
        val scope = testScope()
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(root.absolutePath))
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            val scans = controller.scanStartCount
            controller.onBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
            advanceUntilIdle()
            assertTrue(controller.uiState.value.usbOnline)
            assertEquals(scans, controller.scanStartCount)
            assertTrue(controller.usbResourceReleaseCount >= 1)
            assertTrue(
                controller.uiState.value.usbHostState == UsbHostState.USB_READY ||
                    controller.uiState.value.usbHostState == UsbHostState.USB_ONLINE
            )
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun ejectReleasesUsbResourcesEvenIfStorageManagerStillListsVolume() = runTest {
        val root = createTempDirectory("rec-eject").toFile()
        val scope = testScope()
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(root.absolutePath))
            var releases = 0
            val controller = UsbLifecycleController(
                snapshotVolumes = { snapshots.toList() },
                scanner = LibraryMediaScanner(
                    repository = repo,
                    metadataReader = FakeMetadataReader(),
                    ioDispatcher = UnconfinedTestDispatcher()
                ),
                repository = repo,
                scope = scope,
                now = { 1_000L },
                onForceReleaseUsbResources = { releases += 1 }
            )
            controller.start()
            advanceUntilIdle()
            assertTrue(controller.uiState.value.usbOnline)
            controller.onBroadcast(Intent.ACTION_MEDIA_EJECT)
            advanceUntilIdle()
            assertTrue(releases >= 1)
            assertTrue(controller.usbResourceReleaseCount >= 1)
            assertTrue(controller.uiState.value.usbOnline)
            assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun unmountWithEmptyStorageManagerGoesOfflineAndKeepsCache() = runTest {
        val root = createTempDirectory("rec-gone").toFile()
        val scope = testScope()
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(root.absolutePath))
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            snapshots.clear()
            controller.onBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
            advanceUntilIdle()
            assertFalse(controller.uiState.value.usbOnline)
            assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
            assertTrue(controller.usbResourceReleaseCount >= 1)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun manualRescanFindsVolumeWithoutMountBroadcast() = runTest {
        val root = createTempDirectory("rec-manual").toFile()
        val scope = testScope()
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf<VolumeSnapshot>()
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            assertEquals(UsbHostState.USB_NOT_DETECTED, controller.uiState.value.usbHostState)
            assertFalse(controller.uiState.value.usbOnline)
            snapshots += usbSnapshot(root.absolutePath)
            controller.manualRescan()
            advanceUntilIdle()
            assertEquals("MANUAL_RESCAN", controller.lastRecoveryAction)
            assertTrue(controller.uiState.value.usbOnline)
            assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun manualRescanWithNoVolumeStaysNotDetected() = runTest {
        val scope = testScope()
        try {
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf<VolumeSnapshot>()
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            controller.manualRescan()
            advanceUntilIdle()
            assertFalse(controller.uiState.value.usbOnline)
            assertEquals(UsbHostState.USB_NOT_DETECTED, controller.uiState.value.usbHostState)
            assertTrue(controller.uiState.value.diagnosticMessage!!.contains("MusicLoop không thể mount USB"))
            assertTrue(controller.uiState.value.statusMessage.contains("ANDROID_USB_NOT_DETECTED"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun pollingDetectsNewVolumeAndRestoresCache() = runTest {
        val root = createTempDirectory("rec-poll").toFile()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(root.absolutePath))
            val controller = controller(repo, snapshots, scope, pollIntervalMs = 4_000L)
            controller.start()
            advanceUntilIdle()
            snapshots.clear()
            controller.onBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
            advanceUntilIdle()
            assertEquals(UsbHostState.USB_OFFLINE, controller.uiState.value.usbHostState)
            val scansBefore = controller.scanStartCount
            controller.setForegroundPolling(true)
            snapshots += usbSnapshot(root.absolutePath)
            advanceTimeBy(4_000L)
            runCurrent()
            controller.setForegroundPolling(false)
            advanceUntilIdle()
            assertTrue(controller.uiState.value.usbOnline)
            assertTrue(controller.uiState.value.media.any { it.fileName == "song.mp3" })
            assertTrue(controller.scanStartCount > scansBefore)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun pollingFindsNothingDoesNotPretendOnlineOrRescan() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf<VolumeSnapshot>()
            val controller = controller(repo, snapshots, scope, pollIntervalMs = 4_000L)
            controller.start()
            advanceUntilIdle()
            val scans = controller.scanStartCount
            val snapshotsBefore = controller.storageSnapshotCount
            controller.setForegroundPolling(true)
            advanceTimeBy(4_000L)
            runCurrent()
            controller.setForegroundPolling(false)
            advanceUntilIdle()
            assertFalse(controller.uiState.value.usbOnline)
            assertEquals(UsbHostState.USB_NOT_DETECTED, controller.uiState.value.usbHostState)
            assertEquals(scans, controller.scanStartCount)
            assertTrue(controller.storageSnapshotCount > snapshotsBefore)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun pollingDoesNotDuplicateScanWhileAlreadyOnline() = runTest {
        val root = createTempDirectory("rec-dup").toFile()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(root.absolutePath))
            val controller = controller(repo, snapshots, scope, pollIntervalMs = 4_000L)
            controller.start()
            advanceUntilIdle()
            val scans = controller.scanStartCount
            val upserts = repo.upsertMediaBatchSizes.toList()
            controller.setForegroundPolling(true)
            advanceTimeBy(12_000L)
            runCurrent()
            controller.setForegroundPolling(false)
            advanceUntilIdle()
            assertEquals(scans, controller.scanStartCount)
            assertEquals(upserts, repo.upsertMediaBatchSizes)
            assertTrue(controller.uiState.value.usbOnline)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun cachedLibraryRestoredBeforePollScanCompletes() = runTest {
        val first = createTempDirectory("rec-cache-a").toFile()
        val second = createTempDirectory("rec-cache-b").toFile()
        val scanDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            first.resolve("song.mp3").writeText("ok")
            second.resolve("song.mp3").writeText("ok")
            second.resolve("extra.mp4").writeText("vid")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(first.absolutePath, uuid = "ABCD-EF01"))
            val scanner = LibraryMediaScanner(
                repository = repo,
                metadataReader = FakeMetadataReader(),
                ioDispatcher = scanDispatcher
            )
            val controller = UsbLifecycleController(
                snapshotVolumes = { snapshots.toList() },
                scanner = scanner,
                repository = repo,
                scope = scope,
                now = { 2_000L },
                pollIntervalMs = 4_000L
            )
            controller.start()
            advanceUntilIdle()
            snapshots.clear()
            controller.onBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
            advanceUntilIdle()
            snapshots += usbSnapshot(second.absolutePath, uuid = "ABCD-EF01")
            controller.manualRescan()
            assertEquals(1, controller.lastRestoreReport?.cachedItems)
            assertTrue(controller.uiState.value.media.any { it.fileName == "song.mp3" })
            assertEquals(null, controller.lastRestoreReport?.scanCompletedMs)
            advanceUntilIdle()
            assertTrue(repo.mediaForVolume("ABCD-EF01").any { it.fileName == "extra.mp4" })
        } finally {
            scope.cancel()
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }

    private fun testScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    }

    private fun controller(
        repo: InMemoryLibraryRepository,
        snapshots: MutableList<VolumeSnapshot>,
        scope: CoroutineScope,
        pollIntervalMs: Long = 60_000L
    ): UsbLifecycleController {
        val scanner = LibraryMediaScanner(
            repository = repo,
            metadataReader = FakeMetadataReader(),
            ioDispatcher = UnconfinedTestDispatcher()
        )
        return UsbLifecycleController(
            snapshotVolumes = { snapshots.toList() },
            scanner = scanner,
            repository = repo,
            scope = scope,
            now = { 1_000L },
            pollIntervalMs = pollIntervalMs
        )
    }

    private fun usbSnapshot(rootPath: String, uuid: String = "AAAA-AAAA"): VolumeSnapshot {
        return VolumeSnapshot(
            description = "USB DISK",
            state = "mounted",
            removable = true,
            isPrimary = false,
            uuid = uuid,
            rootPath = rootPath,
            exists = true,
            isDirectory = true,
            canRead = true,
            listFilesNonNull = true
        )
    }
}
