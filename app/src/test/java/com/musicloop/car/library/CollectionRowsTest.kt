package com.musicloop.car.library

import com.musicloop.car.database.FavoriteEntity
import com.musicloop.car.database.PlaylistItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionRowsTest {

    @Test
    fun favoriteRemainsVisibleWhenUsbOfflineWithoutFakingAvailability() {
        val favorite = FavoriteEntity(
            volumeId = "VOL",
            relativePath = "a.mp3",
            fileName = "a.mp3",
            title = "Night",
            artist = "Band",
            album = null,
            mediaType = "AUDIO",
            addedAt = 1L
        )
        val live = MediaListRow(
            id = 1L,
            volumeId = "VOL",
            relativePath = "a.mp3",
            fileName = "a.mp3",
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = 1L,
            durationMs = 1L,
            title = "Night",
            artist = "Band",
            album = null,
            scanStatus = "READY"
        )
        val offline = CollectionRows.fromFavorites(listOf(favorite), listOf(live), usbOnline = false, query = "", sort = LibrarySort.A_Z)
        assertEquals(1, offline.size)
        assertFalse(offline.single().available)
        val online = CollectionRows.fromFavorites(listOf(favorite), listOf(live), usbOnline = true, query = "", sort = LibrarySort.A_Z)
        assertTrue(online.single().available)
        val missing = CollectionRows.fromFavorites(listOf(favorite), emptyList(), usbOnline = true, query = "", sort = LibrarySort.A_Z)
        assertFalse(missing.single().available)
        assertEquals("Night", missing.single().title)
    }

    @Test
    fun playlistItemsAreKeptWhenOffline() {
        val item = PlaylistItemEntity(
            playlistId = 1L,
            position = 0,
            volumeId = "VOL",
            relativePath = "clip.mp3",
            fileName = "clip.mp3",
            title = "Clip",
            artist = null,
            album = null,
            mediaType = "AUDIO",
            addedAt = 1L
        )
        val rows = CollectionRows.fromPlaylistItems(listOf(item), emptyList(), usbOnline = false, query = "")
        assertEquals("clip.mp3", rows.single().relativePath)
        assertFalse(rows.single().available)
    }
}
