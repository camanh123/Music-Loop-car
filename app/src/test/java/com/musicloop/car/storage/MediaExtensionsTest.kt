package com.musicloop.car.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExtensionsTest {
    @Test
    fun acceptsAudioCaseInsensitive() {
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("song.mp3"))
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("SONG.MP3"))
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("a.FlAc"))
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("a.aac"))
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("a.m4a"))
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("a.wav"))
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("a.ogg"))
    }

    @Test
    fun acceptsVideoWhitelist() {
        assertEquals(MediaKind.VIDEO, MediaExtensions.kindOf("clip.mp4"))
        assertEquals(MediaKind.VIDEO, MediaExtensions.kindOf("clip.MKV"))
        assertEquals(MediaKind.VIDEO, MediaExtensions.kindOf("clip.avi"))
        assertEquals(MediaKind.VIDEO, MediaExtensions.kindOf("clip.ts"))
    }

    @Test
    fun rejectsUnsupportedNames() {
        assertNull(MediaExtensions.kindOf("notes.txt"))
        assertNull(MediaExtensions.kindOf("song"))
        assertNull(MediaExtensions.kindOf(".mp3"))
        assertNull(MediaExtensions.kindOf("photo.jpg"))
    }
}
