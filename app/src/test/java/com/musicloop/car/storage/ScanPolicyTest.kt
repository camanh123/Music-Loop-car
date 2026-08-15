package com.musicloop.car.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPolicyTest {
    @Test
    fun forbidsInternalAndSystemRoots() {
        assertTrue(ScanPolicy.isForbiddenScanRoot("/data"))
        assertTrue(ScanPolicy.isForbiddenScanRoot("/data/media/0"))
        assertTrue(ScanPolicy.isForbiddenScanRoot("/system"))
        assertTrue(ScanPolicy.isForbiddenScanRoot("/proc/1"))
        assertTrue(ScanPolicy.isForbiddenScanRoot("/storage/emulated/0"))
        assertTrue(ScanPolicy.isForbiddenScanRoot("/"))
    }

    @Test
    fun allowsRemovableVolumeRootsWithoutHardcodedNames() {
        assertFalse(ScanPolicy.isForbiddenScanRoot("/storage/AAAA-AAAA"))
        assertFalse(ScanPolicy.isForbiddenScanRoot("/mnt/media_rw/1234-5678"))
        assertFalse(ScanPolicy.isForbiddenScanRoot("/mnt/usb/disk"))
    }

    @Test
    fun hiddenDotNamesAreSkipped() {
        assertTrue(ScanPolicy.isHiddenName(".hidden"))
        assertTrue(ScanPolicy.isHiddenName(".android_secure"))
        assertFalse(ScanPolicy.isHiddenName("Music"))
        assertFalse(ScanPolicy.isHiddenName("song.mp3"))
    }
}
