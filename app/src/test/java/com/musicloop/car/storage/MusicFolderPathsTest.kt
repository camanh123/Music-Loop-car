package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicFolderPathsTest {

    @Test
    fun relativePathFromUsb1Music() {
        val relative = MusicFolderPaths.relativeToVolume("/storage/USB1", "/storage/USB1/Music")
        assertEquals("Music", relative)
    }

    @Test
    fun nestedRelativePath() {
        val relative = MusicFolderPaths.relativeToVolume(
            "/storage/USB1",
            "/storage/USB1/Music/Việt Nam"
        )
        assertEquals("Music/Việt Nam", relative)
    }

    @Test
    fun volumeRootHasEmptyRelativePath() {
        assertEquals("", MusicFolderPaths.relativeToVolume("/storage/USB1", "/storage/USB1"))
    }

    @Test
    fun relativePathOutsideVolumeIsNull() {
        assertNull(MusicFolderPaths.relativeToVolume("/storage/USB1", "/storage/USB2/Music"))
    }

    @Test
    fun joinRestoresAbsolutePath() {
        assertEquals(
            "/storage/USB2/Music",
            MusicFolderPaths.join("/storage/USB2", "Music")
        )
    }

    @Test
    fun fatUuidExtractedFromMountPath() {
        assertEquals("1A2B-3C4D", MusicFolderPaths.volumeIdentityFromPath("/storage/1A2B-3C4D"))
        assertNull(MusicFolderPaths.volumeIdentityFromPath("/storage/USB1"))
    }

    @Test
    fun displayLabelMatchesAutomotiveCopy() {
        assertEquals("USB / Music", MusicFolderPaths.displayMusicFolder("USB DISK", "Music"))
        assertEquals("USB / Music / Việt Nam", MusicFolderPaths.displayMusicFolder("USB", "Music/Việt Nam"))
    }
}
