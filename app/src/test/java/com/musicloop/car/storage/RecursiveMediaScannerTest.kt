package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class RecursiveMediaScannerTest {

    @Test
    fun findsNestedAndUppercaseAudioWithinDepth() {
        val root = createTempDirectory("usb-poc-nested").toFile()
        try {
            root.resolve("song.mp3").writeText("mp3")
            root.resolve("CLIP.MP4").writeText("mp4")
            val nested = root.resolve("Music/Album").apply { mkdirs() }
            nested.resolve("track.FLAC").writeText("flac")
            val scanner = RecursiveMediaScanner()
            val result = scanner.scan(root)
            assertEquals(2, result.audioCount)
            assertEquals(1, result.videoCount)
            assertTrue(result.audioByExtension.containsKey("mp3"))
            assertTrue(result.audioByExtension.containsKey("flac"))
            assertTrue(result.videoByExtension.containsKey("mp4"))
            assertTrue(result.mediaFilesReadable)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotExceedMaxDepth() {
        val root = createTempDirectory("usb-poc-depth").toFile()
        try {
            val deep = root.resolve("a/b/c/d").apply { mkdirs() }
            deep.resolve("too-deep.mp3").writeText("x")
            val atLimit = root.resolve("a/b/c").apply { mkdirs() }
            atLimit.resolve("ok.mp3").writeText("y")
            val result = RecursiveMediaScanner(maxDepth = 3).scan(root)
            assertEquals(1, result.audioCount)
            assertEquals("ok.mp3", result.samples.single().absolutePath.substringAfterLast('/'))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun respectsMaxFilesLimit() {
        val root = createTempDirectory("usb-poc-limit").toFile()
        try {
            repeat(8) { index ->
                root.resolve("song$index.mp3").writeText("data$index")
            }
            val result = RecursiveMediaScanner(maxFiles = 5).scan(root)
            assertEquals(5, result.audioCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun skipsHiddenDirectoryAndDoesNotAbort() {
        val root = createTempDirectory("usb-poc-hidden").toFile()
        try {
            root.resolve("visible.mp3").writeText("ok")
            val hidden = root.resolve(".secret").apply { mkdirs() }
            hidden.resolve("hidden.mp3").writeText("no")
            val result = RecursiveMediaScanner().scan(root)
            assertEquals(1, result.audioCount)
            assertTrue(result.samples.none { it.absolutePath.contains(".secret") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun stillScansWhenRootItselfIsAMountSymlink() {
        val real = createTempDirectory("usb-poc-mount-real").toFile()
        val parent = createTempDirectory("usb-poc-mount-parent").toFile()
        try {
            real.resolve("song.mp3").writeText("ok")
            val mount = parent.resolve("USB_MOUNT")
            java.nio.file.Files.createSymbolicLink(mount.toPath(), real.toPath())
            val result = RecursiveMediaScanner().scan(mount)
            assertEquals(1, result.audioCount)
            assertTrue(result.mediaFilesReadable)
        } finally {
            parent.deleteRecursively()
            real.deleteRecursively()
        }
    }

    @Test
    fun skipsSymlinkAndContinues() {
        val root = createTempDirectory("usb-poc-link").toFile()
        val outside = createTempDirectory("usb-poc-link-target").toFile()
        try {
            root.resolve("real.mp3").writeText("ok")
            outside.resolve("via-link.mp3").writeText("no")
            Files.createSymbolicLink(root.resolve("linkdir").toPath(), outside.toPath())
            val scanner = RecursiveMediaScanner(
                isSymlink = { file -> file.name == "linkdir" || RecursiveMediaScanner.isSymbolicLink(file) }
            )
            val result = scanner.scan(root)
            assertEquals(1, result.audioCount)
            assertTrue(result.samples.none { it.absolutePath.contains("via-link") })
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun unreadableChildDoesNotAbortScan() {
        val root = createTempDirectory("usb-poc-unreadable").toFile()
        try {
            root.resolve("keep.wav").writeText("ok")
            val blocked = root.resolve("blocked").apply { mkdirs() }
            blocked.resolve("miss.mp3").writeText("no")
            val scanner = RecursiveMediaScanner(
                listChildren = { dir ->
                    if (dir.name == "blocked") null else dir.listFiles()
                }
            )
            val result = scanner.scan(root)
            assertEquals(1, result.audioCount)
            assertEquals("keep.wav", result.samples.single().absolutePath.substringAfterLast('/'))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesInternalStorageRoots() {
        val result = RecursiveMediaScanner().scan(java.io.File("/data"))
        assertFalse(result.scanned)
        assertEquals(0, result.audioCount)
    }

    @Test
    fun metadataIsNotRequiredForReadablePass() {
        val root = createTempDirectory("usb-poc-stream").toFile()
        try {
            root.resolve("plain.mp3").writeText("bytes")
            val result = RecursiveMediaScanner().scan(root)
            assertTrue(result.samples.single().streamReadPass)
            assertTrue(result.mediaFilesReadable)
        } finally {
            root.deleteRecursively()
        }
    }
}
