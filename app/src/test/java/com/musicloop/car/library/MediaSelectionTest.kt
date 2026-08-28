package com.musicloop.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSelectionTest {

    @Test
    fun longPressEntersSelectionAndTapToggles() {
        val first = audio("a.mp3")
        val second = audio("b.mp3")
        var state = MediaSelection.enter(LibraryTab.MUSIC, first)
        assertTrue(state.active)
        assertEquals(1, state.count)
        state = MediaSelection.toggle(state, second)
        assertEquals(2, state.count)
        state = MediaSelection.toggle(state, first)
        assertEquals(1, state.count)
        assertFalse(state.contains(first))
        assertTrue(state.contains(second))
    }

    @Test
    fun selectAllUsesOnlyCurrentVisibleRows() {
        val visible = listOf(audio("keep.mp3"), audio("also.mp3"))
        val hidden = audio("hidden.mp3")
        var state = MediaSelection.enter(LibraryTab.MUSIC, hidden)
        assertTrue(state.contains(hidden))
        state = MediaSelection.selectAll(state, visible)
        assertEquals(2, state.count)
        assertFalse(state.contains(hidden))
        assertTrue(state.contains(visible[0]))
        assertTrue(state.contains(visible[1]))
    }

    @Test
    fun musicAndVideoSelectionsDoNotMix() {
        val song = audio("a.mp3")
        val clip = video("b.mp4")
        val music = MediaSelection.enter(LibraryTab.MUSIC, song)
        assertFalse(MediaSelection.canSelect(LibraryTab.MUSIC, clip))
        val mixed = MediaSelection.toggle(music, clip)
        assertEquals(music.selected, mixed.selected)
        val video = MediaSelection.enter(LibraryTab.VIDEO, clip)
        assertTrue(video.contains(clip))
        assertFalse(MediaSelection.canSelect(LibraryTab.VIDEO, song))
    }

    @Test
    fun favoritesTabDoesNotEnterUsbSelection() {
        val song = audio("a.mp3")
        val state = MediaSelection.enter(LibraryTab.FAVORITES, song)
        assertFalse(state.active)
        assertTrue(state.selected.isEmpty())
    }

    private fun audio(name: String): MediaListRow {
        return MediaListRow(
            id = name.hashCode().toLong(),
            volumeId = "AAAA-AAAA",
            relativePath = name,
            fileName = name,
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = 10L,
            durationMs = 1000L,
            title = name,
            artist = "Artist",
            album = null,
            scanStatus = "COMPLETE"
        )
    }

    private fun video(name: String): MediaListRow {
        return audio(name).copy(
            mediaType = "VIDEO",
            extension = "mp4",
            fileName = name,
            relativePath = name
        )
    }
}
