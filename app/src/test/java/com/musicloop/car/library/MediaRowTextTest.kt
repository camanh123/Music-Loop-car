package com.musicloop.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRowTextTest {

    @Test
    fun videoHidesSubtitleWhenTitleMatchesFilename() {
        val row = video(fileName = "clip-09.mp4", title = "clip-09.mp4")
        assertEquals("clip-09.mp4", MediaRowText.title(row))
        assertNull(MediaRowText.subtitle(row))
        assertTrue(MediaRowText.isDuplicateTitleAndFile(row.title, row.fileName))
    }

    @Test
    fun videoHidesSubtitleWhenNormalizedTitleMatchesStem() {
        val row = video(fileName = "Dashcam_Left.mp4", title = "dashcam left")
        assertTrue(MediaRowText.isDuplicateTitleAndFile(row.title, row.fileName))
        assertNull(MediaRowText.subtitle(row))
    }

    @Test
    fun videoKeepsFilenameWhenTitleDiffers() {
        val row = video(fileName = "CLIP-09.mp4", title = "Dashcam Left")
        assertEquals("Dashcam Left", MediaRowText.title(row))
        assertEquals("CLIP-09.mp4", MediaRowText.subtitle(row))
        assertFalse(MediaRowText.isDuplicateTitleAndFile(row.title, row.fileName))
    }

    @Test
    fun musicShowsArtistOnlyAndSkipsFilenameDuplicate() {
        val withArtist = audio(fileName = "track-01.mp3", title = "Night Drive", artist = "River Band")
        assertEquals("Night Drive", MediaRowText.title(withArtist))
        assertEquals("River Band", MediaRowText.subtitle(withArtist))
        val noArtist = audio(fileName = "track-01.mp3", title = "track-01.mp3", artist = null)
        assertEquals("track-01.mp3", MediaRowText.title(noArtist))
        assertNull(MediaRowText.subtitle(noArtist))
    }

    @Test
    fun unavailableKeepsReadableMarker() {
        val row = audio(fileName = "gone.mp3", title = "Gone", artist = "Band").copy(available = false)
        assertEquals("Gone", MediaRowText.title(row))
        assertEquals(MediaRowText.UNAVAILABLE, MediaRowText.subtitle(row))
    }

    private fun audio(
        fileName: String,
        title: String?,
        artist: String?
    ): MediaListRow {
        return MediaListRow(
            id = 1L,
            volumeId = "VOL",
            relativePath = fileName,
            fileName = fileName,
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = 1L,
            durationMs = 1L,
            title = title,
            artist = artist,
            album = "Album",
            scanStatus = "READY"
        )
    }

    private fun video(fileName: String, title: String?): MediaListRow {
        return MediaListRow(
            id = 2L,
            volumeId = "VOL",
            relativePath = fileName,
            fileName = fileName,
            extension = "mp4",
            mediaType = "VIDEO",
            sizeBytes = 1L,
            durationMs = 1L,
            title = title,
            artist = null,
            album = null,
            scanStatus = "READY"
        )
    }
}
