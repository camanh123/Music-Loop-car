package com.musicloop.car.database

import com.musicloop.car.scanner.AudioTrack
import com.musicloop.car.scanner.MetadataState
import com.musicloop.car.scanner.PlayableState
import com.musicloop.car.scanner.ScanState
import com.musicloop.car.scanner.TrackRepository
import com.musicloop.car.storage.MusicFolderPaths

class RoomTrackRepository(private val dao: AudioTrackDao) : TrackRepository {

    override fun tracksForVolume(volumeIdentity: String): List<AudioTrack> {
        return dao.tracksForVolume(volumeIdentity).map { it.toDomain() }
    }

    fun find(volumeIdentity: String, relativePath: String): AudioTrack? {
        return dao.find(volumeIdentity, relativePath)?.toDomain()
    }

    override fun upsert(track: AudioTrack): AudioTrack {
        val existing = dao.find(track.volumeIdentity, track.relativePath)
        return if (existing == null) {
            val id = dao.insert(track.copy(id = 0).toEntity())
            track.copy(id = id)
        } else {
            val merged = track.copy(id = existing.id, favorite = existing.favorite || track.favorite)
            dao.update(merged.toEntity())
            merged
        }
    }

    override fun removeConfirmedMissing(
        volumeIdentity: String,
        folderRelative: String,
        presentRelativePaths: Set<String>
    ) {
        val prefix = if (folderRelative.isEmpty()) "" else "${MusicFolderPaths.normalizeRelative(folderRelative)}/"
        val folder = MusicFolderPaths.normalizeRelative(folderRelative)
        val staleIds = dao.tracksForVolume(volumeIdentity).mapNotNull { entity ->
            val relative = entity.relativePath
            val inSelectedFolder = folder.isEmpty() ||
                relative == folder ||
                relative.startsWith(prefix)
            if (inSelectedFolder && relative !in presentRelativePaths) entity.id else null
        }
        if (staleIds.isNotEmpty()) {
            dao.removeByIds(staleIds)
        }
    }

    private fun AudioTrackEntity.toDomain(): AudioTrack {
        return AudioTrack(
            id = id,
            volumeIdentity = volumeIdentity,
            relativePath = relativePath,
            absolutePath = absolutePath,
            filename = filename,
            extension = extension,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            trackNumber = trackNumber,
            durationMs = durationMs,
            fileSize = fileSize,
            lastModified = lastModified,
            artworkKey = artworkKey,
            scanState = enumValueOfOr(scanState, ScanState.DISCOVERED),
            metadataState = enumValueOfOr(metadataState, MetadataState.METADATA_PENDING),
            playableState = enumValueOfOr(playableState, PlayableState.UNKNOWN),
            favorite = favorite,
            verifyFailures = verifyFailures,
            lastVerifiedAt = lastVerifiedAt,
            missingConfirmed = missingConfirmed,
            updatedAt = updatedAt
        )
    }

    private fun AudioTrack.toEntity(): AudioTrackEntity {
        return AudioTrackEntity(
            id = id,
            volumeIdentity = volumeIdentity,
            relativePath = relativePath,
            absolutePath = absolutePath,
            filename = filename,
            extension = extension,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            trackNumber = trackNumber,
            durationMs = durationMs,
            fileSize = fileSize,
            lastModified = lastModified,
            artworkKey = artworkKey,
            scanState = scanState.name,
            metadataState = metadataState.name,
            playableState = playableState.name,
            favorite = favorite,
            verifyFailures = verifyFailures,
            lastVerifiedAt = lastVerifiedAt,
            missingConfirmed = missingConfirmed,
            updatedAt = updatedAt
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOfOr(raw: String, fallback: T): T {
        return try {
            enumValueOf<T>(raw)
        } catch (_: Exception) {
            fallback
        }
    }
}
