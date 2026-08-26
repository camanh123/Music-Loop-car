package com.musicloop.car.database

import com.musicloop.car.library.MediaListRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserCollectionsRepositoryTest {

    @Test
    fun favoriteAddAndRemoveUsesVolumeIdAndRelativePath() = runTest {
        val repo = InMemoryUserCollectionsRepository()
        val song = audio("Music/a.mp3")
        repo.setFavorite(song, true)
        assertTrue(repo.isFavorite("VOL", "Music/a.mp3"))
        assertFalse(repo.isFavorite("VOL", "/mnt/media_rw/VOL/Music/a.mp3"))
        assertEquals("Music/a.mp3", repo.favorites().single().relativePath)
        repo.setFavorite(song, false)
        assertTrue(repo.favorites().isEmpty())
    }

    @Test
    fun favoritePersistenceSurvivesLibraryAbsence() = runTest {
        val repo = InMemoryUserCollectionsRepository()
        repo.setFavorite(audio("keep.mp3", title = "Keep"), true)
        val stored = repo.favorites().single()
        assertEquals("Keep", stored.title)
        assertEquals("keep.mp3", stored.relativePath)
        assertEquals("VOL", stored.volumeId)
    }

    @Test
    fun playlistCreateRenameAndRemove() = runTest {
        val repo = InMemoryUserCollectionsRepository()
        val created = repo.createPlaylist("Road", nowMs = 1L)
        repo.renamePlaylist(created.id, "Night Drive", nowMs = 2L)
        assertEquals("Night Drive", repo.playlists().single().name)
        repo.removePlaylist(created.id)
        assertTrue(repo.playlists().isEmpty())
    }

    @Test
    fun playlistAddAndRemoveItemPreservesOfflineEntry() = runTest {
        val repo = InMemoryUserCollectionsRepository()
        val list = repo.createPlaylist("Mix", nowMs = 1L)
        repo.addToPlaylist(list.id, audio("gone.mp3", title = "Gone"), nowMs = 2L)
        assertEquals(1, repo.playlistItems(list.id).size)
        repo.addToPlaylist(list.id, audio("stay.mp3"), nowMs = 3L)
        repo.removeFromPlaylist(list.id, "VOL", "stay.mp3")
        val remaining = repo.playlistItems(list.id).single()
        assertEquals("gone.mp3", remaining.relativePath)
        assertEquals("Gone", remaining.title)
    }

    private fun audio(relativePath: String, title: String? = relativePath): MediaListRow {
        return MediaListRow(
            id = relativePath.hashCode().toLong(),
            volumeId = "VOL",
            relativePath = relativePath,
            fileName = relativePath.substringAfterLast('/'),
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = 1L,
            durationMs = 1_000L,
            title = title,
            artist = "Artist",
            album = "Album",
            scanStatus = "READY"
        )
    }
}
