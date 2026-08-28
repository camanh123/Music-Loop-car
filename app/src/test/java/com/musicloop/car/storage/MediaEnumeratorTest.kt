package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class MediaEnumeratorTest {

    @Test
    fun filtersExtensionsCaseInsensitiveIncludingNested() {
        val root = createTempDirectory("enum-nested").toFile()
        try {
            root.resolve("song.MP3").writeText("a")
            root.resolve("clip.Mp4").writeText("v")
            root.resolve("notes.txt").writeText("no")
            val nested = root.resolve("Music/Album").apply { mkdirs() }
            nested.resolve("track.FLAC").writeText("f")
            val result = MediaEnumerator().collect(root, maxDepth = 12, maxFiles = 1000)
            val names = result.files.map { it.fileName }.toSet()
            assertEquals(3, result.files.size)
            assertTrue(names.contains("song.MP3"))
            assertTrue(names.contains("clip.Mp4"))
            assertTrue(names.contains("track.FLAC"))
            assertEquals("Music/Album/track.FLAC", result.files.single { it.fileName == "track.FLAC" }.relativePath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun productionDepthScansDeepTreesUnlikePhase1Cap() {
        val root = createTempDirectory("enum-depth").toFile()
        try {
            val deep = root.resolve("a/b/c/d/e/f").apply { mkdirs() }
            deep.resolve("deep.mp3").writeText("x")
            val phase1 = MediaEnumerator().collect(root, maxDepth = ScanPolicy.MAX_DEPTH, maxFiles = 50)
            val production = MediaEnumerator().collect(
                root,
                maxDepth = LibraryScanPolicy.MAX_DEPTH,
                maxFiles = LibraryScanPolicy.MAX_FILES
            )
            assertEquals(0, phase1.files.size)
            assertEquals(1, production.files.size)
            assertEquals("deep.mp3", production.files.single().fileName)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun skipsHiddenDirectories() {
        val root = createTempDirectory("enum-hidden").toFile()
        try {
            root.resolve("visible.wav").writeText("ok")
            root.resolve(".secret").apply { mkdirs() }.resolve("hidden.mp3").writeText("no")
            val result = MediaEnumerator().collect(root, maxDepth = 8, maxFiles = 100)
            assertEquals(1, result.files.size)
            assertEquals("visible.wav", result.files.single().fileName)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun symlinkVolumeRootStillEnumeratesSubdirectories() {
        val target = createTempDirectory("enum-alias-target").toFile()
        val parent = createTempDirectory("enum-alias-parent").toFile()
        try {
            target.resolve("Music").apply { mkdirs() }.resolve("a.mp3").writeText("a")
            target.resolve("Videos").apply { mkdirs() }.resolve("c.mp4").writeText("c")
            val alias = parent.resolve("volume-root")
            Files.createSymbolicLink(alias.toPath(), target.toPath())
            val result = MediaEnumerator().collect(alias, maxDepth = 12, maxFiles = 1000)
            assertEquals(1, result.files.count { it.mediaType == MediaKind.AUDIO })
            assertEquals(1, result.files.count { it.mediaType == MediaKind.VIDEO })
        } finally {
            parent.deleteRecursively()
            target.deleteRecursively()
        }
    }

    @Test
    fun skipsSymlinkChildren() {
        val root = createTempDirectory("enum-link").toFile()
        val outside = createTempDirectory("enum-link-target").toFile()
        try {
            root.resolve("real.mp3").writeText("ok")
            outside.resolve("via-link.mp3").writeText("no")
            Files.createSymbolicLink(root.resolve("linkdir").toPath(), outside.toPath())
            val enumerator = MediaEnumerator(
                isSymlink = { file -> file.name == "linkdir" || MediaEnumerator.isSymbolicLink(file) }
            )
            val result = enumerator.collect(root, maxDepth = 8, maxFiles = 100)
            assertEquals(1, result.files.size)
            assertEquals("real.mp3", result.files.single().fileName)
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun unreadableDirectoryDoesNotAbort() {
        val root = createTempDirectory("enum-unreadable").toFile()
        try {
            root.resolve("keep.ogg").writeText("ok")
            val blocked = root.resolve("blocked").apply { mkdirs() }
            blocked.resolve("miss.mp3").writeText("no")
            val enumerator = MediaEnumerator(
                listChildren = { dir -> if (dir.name == "blocked") null else dir.listFiles() }
            )
            val result = enumerator.collect(root, maxDepth = 8, maxFiles = 100)
            assertEquals(1, result.files.size)
            assertEquals("keep.ogg", result.files.single().fileName)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun relativePathDoesNotUseAbsoluteIdentity() {
        val root = createTempDirectory("enum-rel").toFile()
        try {
            val nested = root.resolve("DCIM/Camera").apply { mkdirs() }
            nested.resolve("video.mp4").writeText("vid")
            val result = MediaEnumerator().collect(root, maxDepth = 8, maxFiles = 10)
            assertEquals("DCIM/Camera/video.mp4", result.files.single().relativePath)
            assertTrue(!result.files.single().relativePath.startsWith("/"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nestedMusicAndVideoFoldersAreEnumerated() {
        val root = createTempDirectory("enum-music-video").toFile()
        try {
            root.resolve("Music").apply { mkdirs() }.resolve("a.mp3").writeText("a")
            root.resolve("Music").resolve("B.MP3").writeText("b")
            root.resolve("Videos").apply { mkdirs() }.resolve("c.mp4").writeText("c")
            root.resolve("Videos").resolve("D.MP4").writeText("d")
            val result = MediaEnumerator().collect(root, maxDepth = 12, maxFiles = 1000)
            assertEquals(2, result.files.count { it.mediaType == MediaKind.AUDIO })
            assertEquals(2, result.files.count { it.mediaType == MediaKind.VIDEO })
            assertEquals("Music/a.mp3", result.files.single { it.fileName == "a.mp3" }.relativePath)
            assertEquals("Videos/D.MP4", result.files.single { it.fileName == "D.MP4" }.relativePath)
        } finally {
            root.deleteRecursively()
        }
    }
}
