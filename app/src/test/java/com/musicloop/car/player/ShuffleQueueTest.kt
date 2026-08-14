package com.musicloop.car.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleQueueTest {

    private fun item(name: String, playable: Boolean = true) = QueueItem(
        id = name.hashCode().toLong(),
        volumeIdentity = "AAAA-AAAA",
        relativePath = name,
        title = name,
        artist = "A",
        album = "B",
        durationMs = 1000,
        filename = name,
        extension = "mp3",
        playable = playable
    )

    @Test
    fun containsEachPlayableTrackOnce() {
        val source = listOf(item("a.mp3"), item("b.mp3", playable = false), item("c.mp3"), item("d.mp3"))
        val shuffled = ShuffleQueue.build(source, item("c.mp3"), Random(1))
        assertEquals(listOf("c.mp3", "a.mp3", "d.mp3").sorted(), shuffled.map { it.filename }.sorted())
        assertEquals("c.mp3", shuffled.first().filename)
        assertEquals(3, shuffled.distinctBy { it.relativePath }.size)
    }

    @Test
    fun doesNotReorderSourceList() {
        val source = listOf(item("a.mp3"), item("b.mp3"), item("c.mp3"))
        val original = source.map { it.filename }
        ShuffleQueue.build(source, null, Random(2))
        assertEquals(original, source.map { it.filename })
    }

    @Test
    fun oneTrackShuffleReturnsThatTrack() {
        val source = listOf(item("only.mp3"))
        val shuffled = ShuffleQueue.build(source, item("only.mp3"), Random(3))
        assertEquals(listOf("only.mp3"), shuffled.map { it.filename })
    }
}

class AutoPlayPolicyTest {

    private val track = QueueItem(
        id = 1,
        volumeIdentity = "AAAA-AAAA",
        relativePath = "a.mp3",
        title = "a",
        artist = "",
        album = "",
        durationMs = 1000,
        filename = "a.mp3",
        extension = "mp3",
        playable = true
    )

    @Test
    fun offRestoresButDoesNotPlay() {
        assertFalse(
            AutoPlayPolicy.shouldStart(
                autoPlayEnabled = false,
                bootStart = true,
                usbReady = true,
                track = track,
                readable = true,
                alreadyAttempted = false
            )
        )
    }

    @Test
    fun onStartsWhenBootUsbAndTrackReady() {
        assertTrue(
            AutoPlayPolicy.shouldStart(
                autoPlayEnabled = true,
                bootStart = true,
                usbReady = true,
                track = track,
                readable = true,
                alreadyAttempted = false
            )
        )
    }

    @Test
    fun missingTrackDoesNotAutoPlay() {
        assertFalse(
            AutoPlayPolicy.shouldStart(
                autoPlayEnabled = true,
                bootStart = true,
                usbReady = true,
                track = null,
                readable = false,
                alreadyAttempted = false
            )
        )
        assertFalse(
            AutoPlayPolicy.shouldStart(
                autoPlayEnabled = true,
                bootStart = true,
                usbReady = true,
                track = track,
                readable = false,
                alreadyAttempted = false
            )
        )
    }

    @Test
    fun usbReinsertWithoutBootDoesNotAutoPlay() {
        assertFalse(
            AutoPlayPolicy.shouldStart(
                autoPlayEnabled = true,
                bootStart = false,
                usbReady = true,
                track = track,
                readable = true,
                alreadyAttempted = false
            )
        )
    }

    @Test
    fun neverRetriesAfterAttempt() {
        assertFalse(
            AutoPlayPolicy.shouldStart(
                autoPlayEnabled = true,
                bootStart = true,
                usbReady = true,
                track = track,
                readable = true,
                alreadyAttempted = true
            )
        )
    }
}
