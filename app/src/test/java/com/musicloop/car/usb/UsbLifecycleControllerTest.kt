package com.musicloop.car.usb

import android.content.Intent
import com.musicloop.car.database.InMemoryLibraryRepository
import com.musicloop.car.database.ScanStatus
import com.musicloop.car.library.FakeMetadataReader
import com.musicloop.car.library.LibraryMediaScanner
import com.musicloop.car.library.ScanUiState
import com.musicloop.car.storage.VolumeSnapshot
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
class UsbLifecycleControllerTest {

    @Test
    fun mountScansReadableRemovableVolume() = runTest {
        val root = createTempDirectory("life-mount").toFile()
        val scope = testScope()
        try {
            root.resolve("song.mp3").writeText("ok")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(root.absolutePath))
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            assertTrue(repo.getVolume("AAAA-AAAA")?.isOnline == true)
            assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
            assertEquals(ScanUiState.COMPLETED, controller.uiState.value.scanState)
            assertTrue(controller.uiState.value.usbOnline)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun unmountMarksOfflineAndKeepsLibrary() = runTest {
        val root = createTempDirectory("life-unmount").toFile()
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
            val volume = repo.getVolume("AAAA-AAAA")!!
            assertFalse(volume.isOnline)
            assertEquals(1, repo.mediaForVolume("AAAA-AAAA").size)
            assertEquals(ScanStatus.COMPLETE, repo.mediaForVolume("AAAA-AAAA").single().scanStatus)
            assertEquals(ScanUiState.USB_OFFLINE, controller.uiState.value.scanState)
            assertFalse(controller.uiState.value.usbOnline)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test
    fun remountWithNewRootRestoresOnlineAndRescans() = runTest {
        val firstRoot = createTempDirectory("life-first").toFile()
        val secondRoot = createTempDirectory("life-second").toFile()
        val scope = testScope()
        try {
            firstRoot.resolve("song.mp3").writeText("ok")
            secondRoot.resolve("song.mp3").writeText("ok")
            secondRoot.resolve("extra.mp4").writeText("vid")
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(usbSnapshot(firstRoot.absolutePath, uuid = "ABCD-EF01"))
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            snapshots.clear()
            controller.onBroadcast(Intent.ACTION_MEDIA_UNMOUNTED)
            advanceUntilIdle()
            snapshots += usbSnapshot(secondRoot.absolutePath, uuid = "ABCD-EF01")
            controller.onBroadcast(Intent.ACTION_MEDIA_MOUNTED)
            advanceUntilIdle()
            val volume = repo.getVolume("ABCD-EF01")!!
            assertTrue(volume.isOnline)
            assertEquals(secondRoot.absolutePath, volume.lastKnownRootPath)
            val names = repo.mediaForVolume("ABCD-EF01").map { it.fileName }.toSet()
            assertTrue(names.contains("song.mp3"))
            assertTrue(names.contains("extra.mp4"))
            assertTrue(controller.uiState.value.usbOnline)
        } finally {
            scope.cancel()
            firstRoot.deleteRecursively()
            secondRoot.deleteRecursively()
        }
    }

    @Test
    fun internalVolumeIsNotScanned() = runTest {
        val scope = testScope()
        try {
            val repo = InMemoryLibraryRepository()
            val snapshots = mutableListOf(
                VolumeSnapshot(
                    description = "Internal shared storage",
                    state = "mounted",
                    removable = false,
                    isPrimary = true,
                    uuid = null,
                    rootPath = "/storage/emulated/0",
                    exists = true,
                    isDirectory = true,
                    canRead = true,
                    listFilesNonNull = true
                )
            )
            val controller = controller(repo, snapshots, scope)
            controller.start()
            advanceUntilIdle()
            assertTrue(repo.getAllVolumes().isEmpty())
            assertEquals(ScanUiState.USB_OFFLINE, controller.uiState.value.scanState)
        } finally {
            scope.cancel()
        }
    }

    private fun testScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    }

    private fun controller(
        repo: InMemoryLibraryRepository,
        snapshots: MutableList<VolumeSnapshot>,
        scope: CoroutineScope
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
            now = { 1_000L }
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
