package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAccessCapabilityTest {

    @Test
    fun offlineWhenVolumeMissing() {
        val access = UsbAccess.classify(null)
        assertEquals(UsbAccessCapability.OFFLINE, access.capability)
        assertFalse(access.readable)
        assertFalse(access.writable)
        assertFalse(access.allowsDelete)
        assertEquals(listOf("USB OFFLINE"), access.statusLabels())
    }

    @Test
    fun readOnlyWhenMountStateIsReadOnly() {
        val access = UsbAccess.classify(snapshot(state = "mounted_ro", canWrite = true))
        assertEquals(UsbAccessCapability.READ_ONLY, access.capability)
        assertTrue(access.readable)
        assertFalse(access.writable)
        assertFalse(access.allowsDelete)
        assertEquals(listOf("USB READABLE", "USB READ-ONLY"), access.statusLabels())
    }

    @Test
    fun readOnlyWhenCanWriteIsFalse() {
        val access = UsbAccess.classify(snapshot(canWrite = false))
        assertEquals(UsbAccessCapability.READ_ONLY, access.capability)
        assertTrue(access.readable)
        assertFalse(access.allowsDelete)
        assertEquals("USB READ-ONLY", access.label)
    }

    @Test
    fun writableWhenMountedAndCanWrite() {
        val access = UsbAccess.classify(snapshot(canWrite = true))
        assertEquals(UsbAccessCapability.WRITABLE, access.capability)
        assertTrue(access.allowsDelete)
        assertEquals(listOf("USB READABLE", "USB WRITABLE"), access.statusLabels())
    }

    @Test
    fun classifyUsesCurrentVolumeIdNotStaleRoot() {
        val snapshots = listOf(snapshot(uuid = "AAAA-AAAA", root = "/mnt/new", canWrite = true))
        val access = UsbAccess.classify(snapshots, "AAAA-AAAA")
        assertTrue(access.allowsDelete)
        val missing = UsbAccess.classify(snapshots, "BBBB-BBBB")
        assertEquals(UsbAccessCapability.OFFLINE, missing.capability)
    }

    private fun snapshot(
        uuid: String = "AAAA-AAAA",
        root: String = "/mnt/media_rw/AAAA-AAAA",
        state: String = "mounted",
        canWrite: Boolean = false
    ): VolumeSnapshot {
        return VolumeSnapshot(
            description = "USB DISK",
            state = state,
            removable = true,
            isPrimary = false,
            uuid = uuid,
            rootPath = root,
            exists = true,
            isDirectory = true,
            canRead = true,
            listFilesNonNull = true,
            canWrite = canWrite
        )
    }
}
