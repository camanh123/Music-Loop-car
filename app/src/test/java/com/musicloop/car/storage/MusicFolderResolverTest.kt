package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFolderResolverTest {

    private val usb1 = UsbVolume("AAAA-AAAA", "USB DISK", "/storage/USB1", "mounted", true)
    private val usb2 = UsbVolume("AAAA-AAAA", "USB DISK", "/storage/USB2", "mounted", true)
    private val otherStick = UsbVolume("BBBB-BBBB", "OTHER", "/storage/USB2", "mounted", true)

    private val savedMusic = MusicFolderRecord(
        absolutePath = "/storage/USB1/Music",
        relativePath = "Music",
        volumeUuid = "AAAA-AAAA",
        volumeLabel = "USB DISK",
        folderName = "Music"
    )

    @Test
    fun restoresFolderAfterRebootWhenUsb1StillPresent() {
        val dirs = setOf("/storage/USB1", "/storage/USB1/Music")
        val resolver = MusicFolderResolver { dirs.contains(it) }
        val result = resolver.resolve(savedMusic, listOf(usb1))
        val found = result as FolderResolveResult.Found
        assertEquals("/storage/USB1/Music", found.absolutePath)
    }

    @Test
    fun restoresFolderWhenMountPathChangesUsb1ToUsb2() {
        val dirs = setOf("/storage/USB2", "/storage/USB2/Music")
        val resolver = MusicFolderResolver { dirs.contains(it) }
        val result = resolver.resolve(savedMusic, listOf(usb2))
        val found = result as FolderResolveResult.Found
        assertEquals("/storage/USB2/Music", found.absolutePath)
        assertEquals("AAAA-AAAA", found.record.volumeUuid)
        assertEquals("Music", found.record.relativePath)
    }

    @Test
    fun waitsWhenRememberedVolumeIsMissing() {
        val dirs = setOf("/storage/USB2", "/storage/USB2/Music")
        val resolver = MusicFolderResolver { dirs.contains(it) }
        val result = resolver.resolve(savedMusic, listOf(otherStick))
        assertEquals(FolderResolveResult.WaitingForUsb, result)
    }

    @Test
    fun doesNotSilentlyPickUnrelatedFolderOnDifferentUuid() {
        val dirs = setOf("/storage/USB2", "/storage/USB2/Music", "/storage/USB2/Other")
        val resolver = MusicFolderResolver { dirs.contains(it) }
        val result = resolver.resolve(savedMusic, listOf(otherStick))
        assertTrue(result is FolderResolveResult.WaitingForUsb)
    }

    @Test
    fun folderNotFoundWhenSameVolumeMissingMusicDir() {
        val dirs = setOf("/storage/USB1")
        val resolver = MusicFolderResolver { dirs.contains(it) }
        val result = resolver.resolve(savedMusic, listOf(usb1))
        assertEquals(FolderResolveResult.FolderNotFound, result)
    }

    @Test
    fun waitingWhenNoUsbMounted() {
        val resolver = MusicFolderResolver { false }
        val result = resolver.resolve(savedMusic, emptyList())
        assertEquals(FolderResolveResult.WaitingForUsb, result)
    }

    @Test
    fun relativeMatchWithoutUuidWhenSingleVolume() {
        val saved = savedMusic.copy(volumeUuid = null)
        val dirs = setOf("/storage/USB2", "/storage/USB2/Music")
        val usb = usb2.copy(uuid = null)
        val resolver = MusicFolderResolver { dirs.contains(it) }
        val result = resolver.resolve(saved, listOf(usb)) as FolderResolveResult.Found
        assertEquals("/storage/USB2/Music", result.absolutePath)
    }

    @Test
    fun ambiguousWhenTwoVolumesShareRelativePathAndNoUuid() {
        val saved = savedMusic.copy(
            volumeUuid = null,
            absolutePath = "/storage/GONE/Music"
        )
        val a = UsbVolume(null, "USB A", "/storage/USB1", "mounted", true)
        val b = UsbVolume(null, "USB B", "/storage/USB2", "mounted", true)
        val dirs = setOf(
            "/storage/USB1", "/storage/USB1/Music",
            "/storage/USB2", "/storage/USB2/Music"
        )
        val resolver = MusicFolderResolver { dirs.contains(it) }
        assertEquals(FolderResolveResult.Ambiguous, resolver.resolve(saved, listOf(a, b)))
    }
}
