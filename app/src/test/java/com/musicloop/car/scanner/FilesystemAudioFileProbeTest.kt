package com.musicloop.car.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilesystemAudioFileProbeTest {

    @Test
    fun discoversNestedAndUppercaseMp3() {
        val root = createTempDir("musicloop-probe")
        try {
            val music = File(root, "Music").apply { mkdirs() }
            File(music, "song1.mp3").writeText("a")
            File(music, "SONG.MP3").writeText("b")
            File(music, "notes.txt").writeText("nope")
            val nested = File(music, "Vietnamese").apply { mkdirs() }
            File(nested, "song3.mp3").writeText("c")
            val album = File(music, "Albums/Album1").apply { mkdirs() }
            File(album, "song4.m4a").writeText("d")

            val probe = FilesystemAudioFileProbe(
                volumeRootProvider = { root.absolutePath },
                isCancelled = { false }
            )
            val result = probe.listAudioFiles(music.absolutePath, root.absolutePath)
            val names = result.files.map { it.filename }.toSet()
            assertTrue(names.contains("song1.mp3"))
            assertTrue(names.contains("SONG.MP3"))
            assertTrue(names.contains("song3.mp3"))
            assertTrue(names.contains("song4.m4a"))
            assertFalse(names.contains("notes.txt"))
            assertTrue(result.files.any { it.relativePath.endsWith("Vietnamese/song3.mp3") })
            assertTrue(result.files.any { it.relativePath.endsWith("Albums/Album1/song4.m4a") })
            assertTrue(result.acceptedAudioFiles >= 4)
            assertTrue(result.rejectedFiles >= 1)
            assertTrue(result.folderExists)
            assertTrue(result.folderIsDirectory)
            assertFalse(result.rootUnreadable)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unreadableChildDirectoryDoesNotAbortScan() {
        val root = createTempDir("musicloop-unreadable")
        try {
            val music = File(root, "Music").apply { mkdirs() }
            File(music, "keep.mp3").writeText("ok")
            val blocked = File(music, "blocked").apply { mkdirs() }
            File(blocked, "hidden.mp3").writeText("hidden")

            val probe = FilesystemAudioFileProbe(
                volumeRootProvider = { root.absolutePath },
                isCancelled = { false },
                listChildren = { dir ->
                    if (dir.name == "blocked") {
                        null
                    } else {
                        FilesystemAudioFileProbe.listChildrenReadOnly(dir)
                    }
                }
            )
            val result = probe.listAudioFiles(music.absolutePath, root.absolutePath)
            assertFalse(result.rootUnreadable)
            assertEquals(listOf("keep.mp3"), result.files.map { it.filename })
            assertEquals(1, result.acceptedAudioFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingRootIsUnreadableWithoutThrowing() {
        val probe = FilesystemAudioFileProbe(
            volumeRootProvider = { "/storage/USB1" },
            isCancelled = { false }
        )
        val result = probe.listAudioFiles("/tmp/musicloop-missing-folder-xyz", "/storage/USB1")
        assertTrue(result.rootUnreadable)
        assertTrue(result.files.isEmpty())
        assertFalse(result.folderExists)
    }
}
