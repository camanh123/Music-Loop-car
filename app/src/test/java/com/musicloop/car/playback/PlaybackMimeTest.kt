package com.musicloop.car.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMimeTest {
    @Test
    fun mapsCommonAudioAndVideoExtensions() {
        assertEquals("audio/mpeg", PlaybackMime.fromFileName("song.mp3"))
        assertEquals("audio/flac", PlaybackMime.fromFileName("live.FLAC"))
        assertEquals("video/mp4", PlaybackMime.fromFileName("clip.mp4"))
        assertEquals("video/x-matroska", PlaybackMime.fromFileName("movie.mkv"))
        assertNull(PlaybackMime.fromFileName("notes.txt"))
    }

    @Test
    fun detectsVideoFileNames() {
        assertTrue(PlaybackMime.isVideoFileName("a.mp4"))
        assertFalse(PlaybackMime.isVideoFileName("a.mp3"))
        assertFalse(PlaybackMime.isVideoFileName("a.txt"))
    }
}
