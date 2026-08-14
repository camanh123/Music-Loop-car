package com.musicloop.car.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackPathResolverTest {

    @Test
    fun resolvesThroughCurrentVolumeRootNotStaleUsbPath() {
        val relative = "Music/Vietnam/song.mp3"
        val usb1 = PlaybackPathResolver.resolveAbsolute("/storage/USB1", relative)
        val usb2 = PlaybackPathResolver.resolveAbsolute("/storage/USB2", relative)
        assertEquals("/storage/USB1/Music/Vietnam/song.mp3", usb1)
        assertEquals("/storage/USB2/Music/Vietnam/song.mp3", usb2)
        assertFalse(usb1 == usb2)
    }

    @Test
    fun missingVolumeOrRelativePathReturnsNull() {
        assertEquals(null, PlaybackPathResolver.resolveAbsolute(null, "Music/song.mp3"))
        assertEquals(null, PlaybackPathResolver.resolveAbsolute("/storage/USB1", ""))
        assertEquals(null, PlaybackPathResolver.resolveReadable("/storage/USB1", "missing/song.mp3"))
    }

    @Test
    fun readableFileMustExist() {
        val file = File.createTempFile("musicloop", ".mp3")
        try {
            assertTrue(PlaybackPathResolver.isReadableFile(file.absolutePath))
            val relative = file.name
            val resolved = PlaybackPathResolver.resolveReadable(file.parent, relative)
            assertEquals(file.absolutePath, resolved)
        } finally {
            file.delete()
        }
    }
}
