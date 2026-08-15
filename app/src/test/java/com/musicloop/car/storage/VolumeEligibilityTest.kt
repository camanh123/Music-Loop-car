package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeEligibilityTest {

    @Test
    fun mountedStateAcceptsMountedAndReadOnly() {
        assertTrue(VolumeEligibility.isMounted("mounted"))
        assertTrue(VolumeEligibility.isMounted("mounted_ro"))
        assertFalse(VolumeEligibility.isMounted("unmounted"))
        assertFalse(VolumeEligibility.isMounted("ejected"))
        assertFalse(VolumeEligibility.isMounted("removed"))
    }

    @Test
    fun removableMountedReadableVolumeIsScannable() {
        val snapshot = snapshot(removable = true, state = "mounted")
        assertTrue(snapshot.scannable)
        assertTrue(snapshot.mounted)
        assertEquals("AAAA-AAAA", snapshot.volumeId)
    }

    @Test
    fun nonRemovableVolumeIsNotScannable() {
        val snapshot = snapshot(removable = true, state = "mounted").copy(removable = false)
        assertFalse(snapshot.scannable)
        assertEquals("not removable", snapshot.skipReason)
    }

    @Test
    fun unmountedVolumeIsNotScannable() {
        val snapshot = snapshot(state = "unmounted")
        assertFalse(snapshot.scannable)
        assertEquals("not mounted", snapshot.skipReason)
    }

    @Test
    fun unresolvedRootIsNotScannable() {
        val snapshot = snapshot().copy(rootPath = null)
        assertFalse(snapshot.scannable)
        assertEquals("root unresolved", snapshot.skipReason)
    }

    @Test
    fun emulatedAndInternalRootsAreRejected() {
        assertFalse(snapshot(rootPath = "/storage/emulated/0").scannable)
        assertFalse(snapshot(rootPath = "/data/media/0").scannable)
        assertEquals("internal/forbidden", snapshot(rootPath = "/system").skipReason)
    }

    @Test
    fun nullListFilesIsNotScannable() {
        val snapshot = snapshot().copy(listFilesNonNull = false)
        assertFalse(snapshot.scannable)
        assertEquals("directory not listable", snapshot.skipReason)
    }

    @Test
    fun mountedRemovableVolumeIsPresentEvenIfNotYetListable() {
        val snapshot = snapshot().copy(listFilesNonNull = false, canRead = false)
        assertTrue(snapshot.presentMountedRemovable)
        assertFalse(snapshot.scannable)
        assertEquals("directory not listable", snapshot.skipReason)
    }

    @Test
    fun remountWithChangedRootKeepsVolumeIdentity() {
        val first = snapshot(uuid = "1234-5678", rootPath = "/mnt/media_rw/disk-a")
        val remount = snapshot(uuid = "1234-5678", rootPath = "/mnt/media_rw/disk-b")
        assertEquals(first.volumeId, remount.volumeId)
        assertNotEquals(first.rootPath, remount.rootPath)
        assertEquals("1234-5678", first.volumeId)
    }

    private fun snapshot(
        uuid: String? = "AAAA-AAAA",
        rootPath: String? = "/mnt/media_rw/AAAA-AAAA",
        state: String = "mounted",
        removable: Boolean = true
    ): VolumeSnapshot {
        return VolumeSnapshot(
            description = "USB DISK",
            state = state,
            removable = removable,
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
