package com.musicloop.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryListQueryTest {

    @Test
    fun musicSearchMatchesTitleArtistAlbumAndFileName() {
        val row = audio(
            fileName = "track-01.mp3",
            title = "Night Drive",
            artist = "River Band",
            album = "Highway EP"
        )
        assertTrue(LibraryListQuery.matchesSearch(row, "night", LibraryTab.MUSIC))
        assertTrue(LibraryListQuery.matchesSearch(row, "RIVER", LibraryTab.MUSIC))
        assertTrue(LibraryListQuery.matchesSearch(row, "highway", LibraryTab.MUSIC))
        assertTrue(LibraryListQuery.matchesSearch(row, "track-01", LibraryTab.MUSIC))
        assertTrue(LibraryListQuery.matchesSearch(row, "night", LibraryTab.FAVORITES))
        assertFalse(LibraryListQuery.matchesSearch(row, "missing", LibraryTab.MUSIC))
    }

    @Test
    fun videoSearchMatchesTitleAndFileNameOnly() {
        val row = video(
            fileName = "clip-09.mp4",
            title = "Dashcam Left",
            artist = "ShouldNotMatch",
            album = "AlbumNo"
        )
        assertTrue(LibraryListQuery.matchesSearch(row, "dashcam", LibraryTab.VIDEO))
        assertTrue(LibraryListQuery.matchesSearch(row, "CLIP-09", LibraryTab.VIDEO))
        assertFalse(LibraryListQuery.matchesSearch(row, "ShouldNotMatch", LibraryTab.VIDEO))
        assertFalse(LibraryListQuery.matchesSearch(row, "AlbumNo", LibraryTab.VIDEO))
    }

    @Test
    fun clearingSearchRestoresFullTabList() {
        val rows = listOf(
            audio(fileName = "a.mp3", title = "Alpha"),
            audio(fileName = "b.mp3", title = "Beta"),
            video(fileName = "c.mp4", title = "Clip")
        )
        val searched = LibraryListQuery.apply(rows, LibraryTab.MUSIC, "beta", LibrarySort.A_Z)
        assertEquals(listOf("b.mp3"), searched.map { it.fileName })
        val cleared = LibraryListQuery.apply(rows, LibraryTab.MUSIC, "  ", LibrarySort.A_Z)
        assertEquals(listOf("a.mp3", "b.mp3"), cleared.map { it.fileName })
    }

    @Test
    fun sortsAzAndZaByDisplayTitle() {
        val rows = listOf(
            audio(fileName = "z.mp3", title = "Zulu"),
            audio(fileName = "a.mp3", title = "Alpha"),
            audio(fileName = "m.mp3", title = "Mike")
        )
        assertEquals(
            listOf("a.mp3", "m.mp3", "z.mp3"),
            LibraryListQuery.sort(rows, LibrarySort.A_Z).map { it.fileName }
        )
        assertEquals(
            listOf("z.mp3", "m.mp3", "a.mp3"),
            LibraryListQuery.sort(rows, LibrarySort.Z_A).map { it.fileName }
        )
    }

    @Test
    fun sortsNewestAndOldestByModifiedTime() {
        val rows = listOf(
            audio(fileName = "old.mp3", title = "Old", modifiedTime = 10L),
            audio(fileName = "new.mp3", title = "New", modifiedTime = 30L),
            audio(fileName = "mid.mp3", title = "Mid", modifiedTime = 20L)
        )
        assertEquals(
            listOf("new.mp3", "mid.mp3", "old.mp3"),
            LibraryListQuery.sort(rows, LibrarySort.NEWEST).map { it.fileName }
        )
        assertEquals(
            listOf("old.mp3", "mid.mp3", "new.mp3"),
            LibraryListQuery.sort(rows, LibrarySort.OLDEST).map { it.fileName }
        )
    }

    @Test
    fun sortsArtistAndAlbumFromExistingFields() {
        val rows = listOf(
            audio(fileName = "2.mp3", title = "B", artist = "Zed", album = "Second"),
            audio(fileName = "1.mp3", title = "A", artist = "Ann", album = "First")
        )
        assertEquals(
            listOf("1.mp3", "2.mp3"),
            LibraryListQuery.sort(rows, LibrarySort.ARTIST).map { it.fileName }
        )
        assertEquals(
            listOf("1.mp3", "2.mp3"),
            LibraryListQuery.sort(rows, LibrarySort.ALBUM).map { it.fileName }
        )
    }

    @Test
    fun currentMediaMatchesVolumeIdAndRelativePathOnly() {
        val row = audio(fileName = "song.mp3", relativePath = "Music/song.mp3", volumeId = "AAAA-AAAA")
        assertTrue(LibraryListQuery.isCurrent(row, "AAAA-AAAA", "Music/song.mp3"))
        assertFalse(LibraryListQuery.isCurrent(row, "AAAA-AAAA", "/mnt/media_rw/AAAA-AAAA/Music/song.mp3"))
        assertFalse(LibraryListQuery.isCurrent(row, "BBBB-BBBB", "Music/song.mp3"))
        assertFalse(LibraryListQuery.isCurrent(row, "", "Music/song.mp3"))
        assertFalse(LibraryListQuery.isCurrent(row, null, null))
    }

    @Test
    fun applyDoesNotMixMusicAndVideo() {
        val rows = listOf(
            audio(fileName = "a.mp3"),
            video(fileName = "a.mp4")
        )
        assertEquals(
            listOf("a.mp3"),
            LibraryListQuery.apply(rows, LibraryTab.MUSIC, "a", LibrarySort.A_Z).map { it.fileName }
        )
        assertEquals(
            listOf("a.mp4"),
            LibraryListQuery.apply(rows, LibraryTab.VIDEO, "a", LibrarySort.A_Z).map { it.fileName }
        )
    }

    private fun audio(
        fileName: String,
        title: String? = fileName,
        artist: String? = null,
        album: String? = null,
        relativePath: String = fileName,
        volumeId: String = "VOL",
        modifiedTime: Long = 0L
    ): MediaListRow {
        return MediaListRow(
            id = fileName.hashCode().toLong(),
            volumeId = volumeId,
            relativePath = relativePath,
            fileName = fileName,
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = 1L,
            durationMs = 1_000L,
            title = title,
            artist = artist,
            album = album,
            scanStatus = "READY",
            modifiedTime = modifiedTime
        )
    }

    private fun video(
        fileName: String,
        title: String? = fileName,
        artist: String? = null,
        album: String? = null
    ): MediaListRow {
        return MediaListRow(
            id = fileName.hashCode().toLong(),
            volumeId = "VOL",
            relativePath = fileName,
            fileName = fileName,
            extension = "mp4",
            mediaType = "VIDEO",
            sizeBytes = 1L,
            durationMs = 1_000L,
            title = title,
            artist = artist,
            album = album,
            scanStatus = "READY"
        )
    }
}
