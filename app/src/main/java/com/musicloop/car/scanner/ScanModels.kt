package com.musicloop.car.scanner

data class DiscoveredFile(
    val relativePath: String,
    val filename: String,
    val extension: String,
    val size: Long,
    val lastModified: Long
)

data class FileSnapshot(
    val exists: Boolean,
    val size: Long,
    val lastModified: Long
)

data class MetadataResult(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val durationMs: Long? = null,
    val success: Boolean,
    val artworkPresent: Boolean = false
)

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.IDLE,
    val processed: Int = 0,
    val total: Int = 0,
    val readyCount: Int = 0,
    val unverifiedCount: Int = 0,
    val unplayableCount: Int = 0,
    val indexedCount: Int = 0
)

data class ScanOutcome(
    val phase: ScanPhase,
    val enumerated: Int,
    val processed: Int
)
