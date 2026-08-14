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
    }
}

class FilenameTitleParserTest {
    @Test
    fun fallbackUsesFilenameWithoutExtension() {
        assertEquals(
            "01 - Em Cua Ngay Hom Qua",
            FilenameTitleParser.titleFromFilename("01 - Em Cua Ngay Hom Qua.mp3")
        )
    }
}
