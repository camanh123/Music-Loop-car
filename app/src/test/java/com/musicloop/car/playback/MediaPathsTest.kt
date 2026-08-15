package com.musicloop.car.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPathsTest {
    @Test
    fun joinsVolumeIdRelativePathWithoutHardcodedUsbNames() {
        val path = MediaPaths.join("/mnt/media_rw/AAAA-AAAA", "Music/Album/song.mp3")
        assertEquals("/mnt/media_rw/AAAA-AAAA/Music/Album/song.mp3", path)
        assertTrue(path!!.contains("AAAA-AAAA"))
        assertTrue(!path.contains("USB1"))
        assertTrue(!path.contains("USB2"))
    }

    @Test
    fun remountRootChangeStillJoinsRelativePath() {
        val first = MediaPaths.join("/mnt/media_rw/disk-a", "DCIM/Camera/clip.mp4")
        val second = MediaPaths.join("/mnt/media_rw/disk-b", "DCIM/Camera/clip.mp4")
        assertEquals("/mnt/media_rw/disk-a/DCIM/Camera/clip.mp4", first)
        assertEquals("/mnt/media_rw/disk-b/DCIM/Camera/clip.mp4", second)
    }

    @Test
    fun rejectsEmptyAndTraversal() {
        assertNull(MediaPaths.join("", "a.mp3"))
        assertNull(MediaPaths.join("/mnt/usb", ""))
        assertNull(MediaPaths.join("/mnt/usb", "../secret.mp3"))
        assertNull(MediaPaths.join("/mnt/usb", "Music/../../etc/x.mp3"))
    }
}
