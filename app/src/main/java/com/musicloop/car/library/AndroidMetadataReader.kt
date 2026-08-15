package com.musicloop.car.library

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Framework metadata reader. Always releases the retriever, including on failure.
 */
class AndroidMetadataReader : MetadataReader {
    override fun read(file: File): ExtractedMetadata {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                ExtractedMetadata(
                    durationMs = duration,
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    width = width,
                    height = height,
                    complete = true
                )
            }
        } catch (_: Exception) {
            ExtractedMetadata.PARTIAL
        }
    }
}
