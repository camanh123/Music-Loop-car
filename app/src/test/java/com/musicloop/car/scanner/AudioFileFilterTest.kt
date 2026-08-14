package com.musicloop.car.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileFilterTest {
    @Test
    fun acceptsCaseInsensitiveExtensions() {
        assertTrue(AudioFileFilter.isAudioFile("song.mp3"))
        assertTrue(AudioFileFilter.isAudioFile("SONG.MP3"))
        assertTrue(AudioFileFilter.isAudioFile("Song.FlAc"))
        assertTrue(AudioFileFilter.isAudioFile("clip.M4A"))
        assertTrue(AudioFileFilter.isAudioFile("a.aac"))
        assertTrue(AudioFileFilter.isAudioFile("a.wav"))
        assertTrue(AudioFileFilter.isAudioFile("a.ogg"))
        assertFalse(AudioFileFilter.isAudioFile("notes.txt"))
        assertFalse(AudioFileFilter.isAudioFile("song"))
        assertFalse(AudioFileFilter.isAudioFile("video.mp4"))
        assertFalse(AudioFileFilter.isAudioFile("video.MP4"))
    }
}

class FilenameTitleParserTest {
    @Test
    fun fallbackKeepsTrackNumberPrefix() {
        assertEquals(
            "01 - Song Name",
            FilenameTitleParser.titleFromFilename("01 - Song Name.mp3")
        )
        assertEquals(
            "SONG",
            FilenameTitleParser.titleFromFilename("SONG.MP3")
        )
    }
}
