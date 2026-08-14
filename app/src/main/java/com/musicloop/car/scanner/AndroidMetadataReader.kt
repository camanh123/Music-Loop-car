package com.musicloop.car.scanner

import android.media.MediaMetadataRetriever

/**
 * Read-only metadata extraction. Never writes tags or files.
 * Artwork bytes are not decoded here.
 *
 * Failure is enrichment-only: the scanner still indexes the file.
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
        } catch (error: Exception) {
            ScannerLog.error("MediaMetadataRetriever", error)
            MetadataResult(
                success = false,
                errorClass = error.javaClass.name,
                errorMessage = error.message
            )
        } finally {
            try {
                retriever.release()
            } catch (error: Exception) {
                ScannerLog.error("MediaMetadataRetriever.release", error)
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
