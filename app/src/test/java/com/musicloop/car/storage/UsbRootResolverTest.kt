package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbRootResolverTest {

    @Test
    fun prefersListableAliasWhenFirstCandidateCannotList() {
        val chosen = UsbRootResolver.selectRoot(
            listOf("/mnt/media_rw/AAAA-AAAA", "/storage/AAAA-AAAA")
        ) { path ->
            when (path) {
                "/mnt/media_rw/AAAA-AAAA" -> UsbRootResolver.Probe(
                    exists = true,
                    isDirectory = true,
                    listFilesNonNull = false
                )
                "/storage/AAAA-AAAA" -> UsbRootResolver.Probe(
                    exists = true,
                    isDirectory = true,
                    listFilesNonNull = true
                )
                else -> UsbRootResolver.Probe()
            }
        }
        assertEquals("/storage/AAAA-AAAA", chosen)
    }

    @Test
    fun fallingBackToExistingDirectoryWhenNothingLists() {
        val chosen = UsbRootResolver.selectRoot(
            listOf("/mnt/media_rw/AAAA-AAAA", "/storage/AAAA-AAAA")
        ) { path ->
            UsbRootResolver.Probe(
                exists = path.startsWith("/mnt/"),
                isDirectory = path.startsWith("/mnt/"),
                listFilesNonNull = false
            )
        }
        assertEquals("/mnt/media_rw/AAAA-AAAA", chosen)
    }

    @Test
    fun storageUuidCandidateUsesVolumeUuidNotHardcodedUsb1() {
        assertEquals("/storage/AAAA-AAAA", UsbRootResolver.storageUuidCandidate("AAAA-AAAA"))
        assertNull(UsbRootResolver.storageUuidCandidate(" "))
        assertNull(UsbRootResolver.storageUuidCandidate(null))
        val candidates = UsbRootResolver.uniqueCandidates(
            listOf("/mnt/media_rw/AAAA-AAAA", null, "/mnt/media_rw/AAAA-AAAA", "/storage/emulated/0")
        )
        assertEquals(listOf("/mnt/media_rw/AAAA-AAAA"), candidates)
    }

    @Test
    fun probeThrowDoesNotDropLaterListableCandidate() {
        val chosen = UsbRootResolver.selectRoot(
            listOf("/mnt/broken", "/storage/AAAA-AAAA")
        ) { path ->
            if (path.contains("broken")) {
                throw IllegalStateException("stat failed")
            }
            UsbRootResolver.Probe(exists = true, isDirectory = true, listFilesNonNull = true)
        }
        assertEquals("/storage/AAAA-AAAA", chosen)
    }

    @Test
    fun emptyCandidatesYieldNull() {
        assertNull(UsbRootResolver.selectRoot(emptyList()) { UsbRootResolver.Probe() })
        assertTrue(UsbRootResolver.uniqueCandidates(listOf(null, "  ", "/data/media/0")).isEmpty())
    }
}
