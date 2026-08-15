package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VolumeIdsTest {
    @Test
    fun uuidIsStableIdentityAndCaseInsensitive() {
        assertEquals("AAAA-AAAA", VolumeIds.resolve("aaaa-aaaa"))
        assertEquals("AAAA-AAAA", VolumeIds.resolve("AAAA-AAAA"))
        assertEquals(VolumeIds.resolve("abcd-ef01"), VolumeIds.resolve("ABCD-EF01"))
    }

    @Test
    fun missingUuidDoesNotUseMountPathBasename() {
        assertEquals(VolumeIds.UNLABELED_USB, VolumeIds.resolve(null))
        assertEquals(VolumeIds.UNLABELED_USB, VolumeIds.resolve("  "))
        assertNotEquals("USB1", VolumeIds.resolve(null))
        assertNotEquals("USB2", VolumeIds.resolve(null))
    }
}
