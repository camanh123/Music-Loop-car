package com.musicloop.car.playlist

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int = 0
)

interface PlaylistRepository {
    fun all(): List<Playlist>
    fun find(id: Long): Playlist?
    fun create(name: String, now: Long): Playlist
    fun rename(id: Long, name: String, now: Long): Playlist?
    fun remove(id: Long)
    fun addTrack(playlistId: Long, trackId: Long): Boolean
    fun removeTrack(playlistId: Long, trackId: Long)
    fun orderedTrackIds(playlistId: Long): List<Long>
}
