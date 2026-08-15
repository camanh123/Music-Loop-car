package com.musicloop.car.library

import java.io.File

data class ExtractedMetadata(
    val durationMs: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val complete: Boolean = true
) {
    companion object {
        val PARTIAL = ExtractedMetadata(complete = false)
    }
}

/**
 * Reads media tags. Implementations must never write to the source file.
 */
fun interface MetadataReader {
    fun read(file: File): ExtractedMetadata
}
