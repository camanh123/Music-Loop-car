package com.musicloop.car.storage

import com.musicloop.car.ui.state.UsbStatus
import com.musicloop.car.ui.state.UsbUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class UsbCoordinatorTest {

    private val usb1 = UsbVolume("AAAA-AAAA", "USB DISK", "/storage/USB1", "mounted", true)
    private val usb2 = UsbVolume("AAAA-AAAA", "USB DISK", "/storage/USB2", "mounted", true)

    @Test
    fun rebootRestoresSavedUsb1MusicFolder() {
        val dirs = mutableSetOf("/storage/USB1", "/storage/USB1/Music")
        val store = InMemoryFolderStore().apply {
            record = MusicFolderRecord.fromSelection("/storage/USB1/Music", usb1)
        }
        val coordinator = coordinator(store, { listOf(usb1) }, dirs)
        coordinator.start()
        assertEquals(UsbStatus.USB_READY, coordinator.currentState().status)
        assertEquals("/storage/USB1/Music", coordinator.currentState().resolvedAbsolutePath)
        assertEquals("USB / Music", coordinator.currentState().musicFolderLabel)
    }

    @Test
    fun usbRemovalKeepsSavedConfigurationAndStaysStable() {
        val dirs = mutableSetOf("/storage/USB1", "/storage/USB1/Music")
        val volumes = mutableListOf(usb1)
        val store = InMemoryFolderStore()
        val coordinator = coordinator(store, { volumes.toList() }, dirs)
        coordinator.start()
        coordinator.onFolderSelected(MusicFolderRecord.fromSelection("/storage/USB1/Music", usb1))
        assertEquals(UsbStatus.USB_READY, coordinator.currentState().status)

        volumes.clear()
        dirs.clear()
        coordinator.refresh()

        assertEquals(UsbStatus.USB_DISCONNECTED, coordinator.currentState().status)
        assertNotNull(store.load())
        assertEquals("/storage/USB1/Music", store.load()?.absolutePath)
        assertNull(coordinator.currentState().resolvedAbsolutePath)
    }

    @Test
    fun usbReinsertOnUsb2RediscoveresRememberedFolder() {
        val dirs = mutableSetOf("/storage/USB1", "/storage/USB1/Music")
        val volumes = mutableListOf(usb1)
        val store = InMemoryFolderStore()
        val coordinator = coordinator(store, { volumes.toList() }, dirs)
        coordinator.start()
        coordinator.onFolderSelected(MusicFolderRecord.fromSelection("/storage/USB1/Music", usb1))

        volumes.clear()
        dirs.clear()
        coordinator.refresh()
        assertEquals(UsbStatus.USB_DISCONNECTED, coordinator.currentState().status)

        volumes += usb2
        dirs += "/storage/USB2"
        dirs += "/storage/USB2/Music"
        coordinator.refresh()

        assertEquals(UsbStatus.USB_READY, coordinator.currentState().status)
        assertEquals("/storage/USB2/Music", coordinator.currentState().resolvedAbsolutePath)
        assertEquals("AAAA-AAAA", store.load()?.volumeUuid)
    }

    @Test
    fun missingUsbBeforeAnySelectionWaitsWithoutCrashing() {
        val coordinator = coordinator(InMemoryFolderStore(), { emptyList() }, mutableSetOf())
        coordinator.start()
        assertEquals(UsbStatus.WAITING_FOR_USB, coordinator.currentState().status)
    }

    @Test
    fun unlabeledUsb1ExposesStableIdentitySoScanCanStart() {
        val usb = UsbVolume(null, "USB DISK", "/storage/USB1", "mounted", true)
        val dirs = mutableSetOf("/storage/USB1", "/storage/USB1/Music")
        val store = InMemoryFolderStore()
        val coordinator = coordinator(store, { listOf(usb) }, dirs)
        coordinator.start()
        coordinator.onFolderSelected(MusicFolderRecord.fromSelection("/storage/USB1/Music", usb))
        assertEquals(UsbStatus.USB_READY, coordinator.currentState().status)
        assertEquals(UsbVolume.UNLABELED_USB_IDENTITY, coordinator.currentState().volumeIdentity)
        assertEquals("/storage/USB1", coordinator.currentState().volumeRootPath)
        assertEquals("/storage/USB1/Music", coordinator.currentState().resolvedAbsolutePath)
        assertEquals(UsbVolume.UNLABELED_USB_IDENTITY, store.load()?.volumeUuid)
    }

    @Test
    fun unlabeledUsb1ToUsb2KeepsSameIdentity() {
        val usb1 = UsbVolume(null, "USB DISK", "/storage/USB1", "mounted", true)
        val usb2 = UsbVolume(null, "USB DISK", "/storage/USB2", "mounted", true)
        val dirs = mutableSetOf("/storage/USB1", "/storage/USB1/Music")
        val volumes = mutableListOf(usb1)
        val store = InMemoryFolderStore()
        val coordinator = coordinator(store, { volumes.toList() }, dirs)
        coordinator.start()
        coordinator.onFolderSelected(MusicFolderRecord.fromSelection("/storage/USB1/Music", usb1))
        assertEquals(UsbVolume.UNLABELED_USB_IDENTITY, coordinator.currentState().volumeIdentity)

        volumes.clear()
        dirs.clear()
        coordinator.refresh()

        volumes += usb2
        dirs += "/storage/USB2"
        dirs += "/storage/USB2/Music"
        coordinator.refresh()

        assertEquals(UsbStatus.USB_READY, coordinator.currentState().status)
        assertEquals("/storage/USB2/Music", coordinator.currentState().resolvedAbsolutePath)
        assertEquals(UsbVolume.UNLABELED_USB_IDENTITY, coordinator.currentState().volumeIdentity)
        assertEquals(UsbVolume.UNLABELED_USB_IDENTITY, store.load()?.volumeUuid)
    }

    @Test
    fun usbPresentWithoutSavedFolderNeedsSelection() {
        val dirs = mutableSetOf("/storage/USB1")
        val coordinator = coordinator(InMemoryFolderStore(), { listOf(usb1) }, dirs)
        coordinator.start()
        assertEquals(UsbStatus.NEEDS_FOLDER, coordinator.currentState().status)
        assertTrue(coordinator.currentState().usbPresent)
    }

    @Test
    fun discoveryExceptionBecomesUsbError() {
        val coordinator = UsbCoordinator(
            discoverVolumes = { error("usb exploded") },
            directoryExists = { false },
            store = InMemoryFolderStore(),
            resolver = MusicFolderResolver { false },
            ioExecutor = ImmediateExecutor,
            mainPoster = MainPoster { it() },
            listener = {}
        )
        coordinator.start()
        assertEquals(UsbStatus.USB_ERROR, coordinator.currentState().status)
    }

    private fun coordinator(
        store: InMemoryFolderStore,
        volumes: () -> List<UsbVolume>,
        dirs: MutableSet<String>
    ): UsbCoordinator {
        val states = mutableListOf<UsbUiState>()
        return UsbCoordinator(
            discoverVolumes = volumes,
            directoryExists = { dirs.contains(it) },
            store = store,
            resolver = MusicFolderResolver { dirs.contains(it) },
            ioExecutor = ImmediateExecutor,
            mainPoster = MainPoster { it() },
            listener = { states += it }
        )
    }

    private class InMemoryFolderStore : MusicFolderRepository {
        var record: MusicFolderRecord? = null
        override fun load(): MusicFolderRecord? = record
        override fun save(record: MusicFolderRecord) {
            this.record = record
        }
    }

    private object ImmediateExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }
}
