package com.musicloop.car.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRepositoryTest {

    @Test
    fun createRenameDeleteDoesNotAffectTracksStore() {
        val repo = InMemoryPlaylistRepository()
        val created = repo.create("Road", 10L)
        assertEquals("Road", created.name)
        repo.rename(created.id, "Night", 20L)
        assertEquals("Night", repo.find(created.id)?.name)
        repo.addTrack(created.id, 5L)
        repo.remove(created.id)
        assertEquals(null, repo.find(created.id))
        assertEquals(emptyList<Long>(), repo.orderedTrackIds(created.id))
    }

    @Test
    fun addTrackPreventsDuplicateAndPreservesOrder() {
        val repo = InMemoryPlaylistRepository()
        val playlist = repo.create("Mix", 1L)
        assertTrue(repo.addTrack(playlist.id, 10L))
        assertTrue(repo.addTrack(playlist.id, 20L))
        assertFalse(repo.addTrack(playlist.id, 10L))
        assertEquals(listOf(10L, 20L), repo.orderedTrackIds(playlist.id))
        repo.removeTrack(playlist.id, 10L)
        assertEquals(listOf(20L), repo.orderedTrackIds(playlist.id))
    }

    @Test
    fun missingTrackIdsAreSkippedByCaller() {
        val repo = InMemoryPlaylistRepository()
        val playlist = repo.create("Live", 1L)
        repo.addTrack(playlist.id, 1L)
        repo.addTrack(playlist.id, 99L)
        val present = setOf(1L)
        val playable = repo.orderedTrackIds(playlist.id).filter { it in present }
        assertEquals(listOf(1L), playable)
    }
}
