package com.musicloop.car.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityReportFormatterTest {
    @Test
    fun reportContainsRequiredSectionsAndPassFail() {
        val volume = VolumeReport(
            index = 1,
            description = "USB DISK",
            state = "mounted",
            removableCandidate = true,
            isPrimary = false,
            uuid = "AAAA-AAAA",
            rootPath = "/mnt/media_rw/AAAA-AAAA",
            exists = true,
            canRead = true,
            isDirectory = true,
            listFilesNonNull = true,
            totalSpaceBytes = 8L * 1024 * 1024 * 1024,
            freeSpaceBytes = 3L * 1024 * 1024 * 1024,
            checks = VerificationChecks(
                volumeDetected = true,
                rootResolved = true,
                directoryReadable = true,
                mediaFilesReadable = true
            ),
            media = MediaScanResult(
                audioCount = 2,
                videoCount = 1,
                audioByExtension = mapOf("mp3" to 2),
                videoByExtension = mapOf("mp4" to 1),
                samples = listOf(
                    MediaReadSample(
                        absolutePath = "/mnt/media_rw/AAAA-AAAA/Music/song1.mp3",
                        kind = MediaKind.AUDIO,
                        sizeBytes = 2L * 1024 * 1024,
                        streamReadPass = true
                    )
                ),
                scanned = true
            )
        )
        val text = CapabilityReportFormatter.format(
            DeviceInfo("CARFU", "UIS7862", 29, "uis7862"),
            listOf(volume)
        )
        assertTrue(text.contains("=== USB STORAGE REPORT ==="))
        assertTrue(text.contains("Android SDK: 29"))
        assertTrue(text.contains("Total Volumes Found: 1"))
        assertTrue(text.contains("Removable Candidate: true"))
        assertTrue(text.contains("[1] Volume Detected: PASS"))
        assertTrue(text.contains("[2] Root Resolved: PASS"))
        assertTrue(text.contains("[3] Directory Readable: PASS"))
        assertTrue(text.contains("[4] Media Files Readable: PASS"))
        assertTrue(text.contains("Found Audio Files: 2"))
        assertTrue(text.contains("Stream Read: PASS"))
        assertFalse(text.contains("/storage/USB1"))
        assertFalse(text.contains("/storage/USB2"))
    }

    @Test
    fun internalVolumeIsReportedButScanSkipped() {
        val internal = VolumeReport(
            index = 1,
            description = "Internal shared storage",
            state = "mounted",
            removableCandidate = false,
            isPrimary = true,
            uuid = null,
            rootPath = "/storage/emulated/0",
            exists = true,
            canRead = true,
            isDirectory = true,
            listFilesNonNull = true,
            totalSpaceBytes = 1L,
            freeSpaceBytes = 1L,
            checks = VerificationChecks.evaluate(
                volumePresent = true,
                rootPath = "/storage/emulated/0",
                exists = true,
                isDirectory = true,
                canRead = true,
                listFilesNonNull = true,
                mediaFilesReadable = false
            ),
            media = MediaScanResult(scanned = false, skipReason = "not removable")
        )
        val text = CapabilityReportFormatter.format(
            DeviceInfo("Brand", "Model", 29, "board"),
            listOf(internal)
        )
        assertTrue(text.contains("Scan skipped: not removable"))
        assertTrue(text.contains("[4] Media Files Readable: FAIL"))
        assertTrue(text.contains("Removable Candidate: false"))
    }
}
