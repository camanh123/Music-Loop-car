package com.musicloop.car.playlist

class InMemoryPlaylistRepository : PlaylistRepository {
    private val playlists = LinkedHashMap<Long, Playlist>()
    private val tracks = LinkedHashMap<Long, MutableList<Long>>()
    private var nextId = 1L

    override fun all(): List<Playlist> {
        return playlists.values.map { it.copy(trackCount = tracks[it.id]?.size ?: 0) }
    }

    override fun find(id: Long): Playlist? {
        val playlist = playlists[id] ?: return null
        return playlist.copy(trackCount = tracks[id]?.size ?: 0)
    }

    override fun create(name: String, now: Long): Playlist {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "playlist name required" }
        val playlist = Playlist(id = nextId++, name = trimmed, createdAt = now, updatedAt = now)
        playlists[playlist.id] = playlist
        tracks[playlist.id] = mutableListOf()
        return playlist
    }

    override fun rename(id: Long, name: String, now: Long): Playlist? {
        val trimmed = name.trim()
        val existing = playlists[id] ?: return null
        if (trimmed.isEmpty()) {
            return find(id)
        }
        val updated = existing.copy(name = trimmed, updatedAt = now)
        playlists[id] = updated
        return find(id)
    }

    override fun remove(id: Long) {
        playlists.remove(id)
        tracks.remove(id)
    }

    override fun addTrack(playlistId: Long, trackId: Long): Boolean {
        val list = tracks[playlistId] ?: return false
        if (list.contains(trackId)) {
            return false
        }
        list.add(trackId)
        return true
    }

    override fun removeTrack(playlistId: Long, trackId: Long) {
        tracks[playlistId]?.remove(trackId)
    }

    override fun orderedTrackIds(playlistId: Long): List<Long> {
        return tracks[playlistId]?.toList().orEmpty()
    }
}
