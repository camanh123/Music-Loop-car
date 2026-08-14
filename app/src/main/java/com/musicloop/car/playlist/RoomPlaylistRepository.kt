package com.musicloop.car.playlist

import com.musicloop.car.database.PlaylistDao
import com.musicloop.car.database.PlaylistEntity
import com.musicloop.car.database.PlaylistTrackEntity

class RoomPlaylistRepository(private val dao: PlaylistDao) : PlaylistRepository {

    override fun all(): List<Playlist> {
        return dao.all().map { it.toDomain(dao.tracks(it.id).size) }
    }

    override fun find(id: Long): Playlist? {
        val entity = dao.find(id) ?: return null
        return entity.toDomain(dao.tracks(id).size)
    }

    override fun create(name: String, now: Long): Playlist {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "playlist name required" }
        val id = dao.insert(PlaylistEntity(name = trimmed, createdAt = now, updatedAt = now))
        return Playlist(id = id, name = trimmed, createdAt = now, updatedAt = now, trackCount = 0)
    }

    override fun rename(id: Long, name: String, now: Long): Playlist? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return find(id)
        }
        val existing = dao.find(id) ?: return null
        val updated = existing.copy(name = trimmed, updatedAt = now)
        dao.update(updated)
        return updated.toDomain(dao.tracks(id).size)
    }

    override fun remove(id: Long) {
        dao.removeTracksForPlaylist(id)
        dao.removePlaylist(id)
    }

    override fun addTrack(playlistId: Long, trackId: Long): Boolean {
        if (dao.find(playlistId) == null) {
            return false
        }
        if (dao.countTrack(playlistId, trackId) > 0) {
            return false
        }
        val position = dao.maxPosition(playlistId) + 1
        dao.insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = position
            )
        )
        return true
    }

    override fun removeTrack(playlistId: Long, trackId: Long) {
        dao.removeTrack(playlistId, trackId)
    }

    override fun orderedTrackIds(playlistId: Long): List<Long> {
        return dao.tracks(playlistId).map { it.trackId }
    }

    private fun PlaylistEntity.toDomain(trackCount: Int): Playlist {
        return Playlist(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            trackCount = trackCount
        )
    }
}
