package com.musicloop.car.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationChecksTest {
    @Test
    fun emptyListFilesStillPassesDirectoryReadable() {
        val checks = VerificationChecks.evaluate(
            volumePresent = true,
            rootPath = "/mnt/media_rw/ABCD-EF01",
            exists = true,
            isDirectory = true,
            canRead = true,
            listFilesNonNull = true,
            mediaFilesReadable = false
        )
        assertTrue(checks.volumeDetected)
        assertTrue(checks.rootResolved)
        assertTrue(checks.directoryReadable)
        assertFalse(checks.mediaFilesReadable)
    }

    @Test
    fun nullListFilesFailsDirectoryReadable() {
        val checks = VerificationChecks.evaluate(
            volumePresent = true,
            rootPath = "/mnt/media_rw/ABCD-EF01",
            exists = true,
            isDirectory = true,
            canRead = true,
            listFilesNonNull = false,
            mediaFilesReadable = false
        )
        assertFalse(checks.directoryReadable)
    }

    @Test
    fun missingRootFailsRootResolved() {
        val checks = VerificationChecks.evaluate(
            volumePresent = true,
            rootPath = null,
            exists = false,
            isDirectory = false,
            canRead = false,
            listFilesNonNull = false,
            mediaFilesReadable = false
        )
        assertTrue(checks.volumeDetected)
        assertFalse(checks.rootResolved)
        assertFalse(checks.directoryReadable)
        assertFalse(checks.mediaFilesReadable)
    }
}
