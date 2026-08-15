package com.musicloop.car.library

import java.io.File

class FakeMetadataReader(
    private val failNames: Set<String> = emptySet(),
    private val throwNames: Set<String> = emptySet()
) : MetadataReader {
    val reads = mutableListOf<String>()

    override fun read(file: File): ExtractedMetadata {
        reads += file.name
        if (file.name in throwNames) {
            throw IllegalStateException("metadata boom")
        }
        if (file.name in failNames) {
            return ExtractedMetadata.PARTIAL
        }
        return ExtractedMetadata(
            durationMs = 12_000L,
            title = file.nameWithoutExtension,
            artist = "Artist",
            album = "Album",
            width = if (file.name.endsWith(".mp4", true)) 1920 else null,
            height = if (file.name.endsWith(".mp4", true)) 1080 else null,
            complete = true
        )
    }
}
