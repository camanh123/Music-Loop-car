package com.musicloop.car.scanner

import android.media.MediaMetadataRetriever

/**
 * Read-only metadata extraction. Never writes tags or files.
 * Artwork bytes are not decoded in Phase 3.
 */
class AndroidMetadataReader : MetadataReader {

    override fun read(absolutePath: String): MetadataResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            MetadataResult(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                trackNumber = parseTrackNumber(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ),
                durationMs = duration,
                success = true,
                artworkPresent = false
            )
        } catch (_: Exception) {
            MetadataResult(success = false)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore retriever release failures.
            }
        }
    }

    private fun parseTrackNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val first = raw.substringBefore('/').trim()
        return first.toIntOrNull()
    }
}
