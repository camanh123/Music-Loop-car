package com.musicloop.car.ui.library

import com.musicloop.car.player.QueueItem
import com.musicloop.car.scanner.AudioTrack
import com.musicloop.car.scanner.DurationFormatter

data class SongRow(
    val id: Long,
    val volumeIdentity: String,
    val relativePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long?,
    val filename: String,
    val extension: String,
    val durationLabel: String,
    val unplayable: Boolean,
    val favorite: Boolean = false
) {
    fun toQueueItem(): QueueItem {
        return QueueItem(
            id = id,
            volumeIdentity = volumeIdentity,
            relativePath = relativePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filename = filename,
            extension = extension,
            playable = !unplayable,
            favorite = favorite
        )
    }

    companion object {
        fun from(track: AudioTrack): SongRow {
            return SongRow(
                id = track.id,
                volumeIdentity = track.volumeIdentity,
                relativePath = track.relativePath,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                filename = track.filename,
                extension = track.extension,
                durationLabel = DurationFormatter.format(track.durationMs),
                unplayable = track.isUnplayable,
                favorite = track.favorite
            )
        }
    }
}
