package com.musicloop.car.scanner

data class AudioTrack(
    val id: Long = 0,
    val volumeIdentity: String,
    val relativePath: String,
    val absolutePath: String,
    val filename: String,
    val extension: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val trackNumber: Int?,
    val durationMs: Long?,
    val fileSize: Long,
    val lastModified: Long,
    val artworkKey: String?,
    val scanState: ScanState,
    val metadataState: MetadataState,
    val playableState: PlayableState,
    val favorite: Boolean,
    val verifyFailures: Int,
    val lastVerifiedAt: Long,
    val missingConfirmed: Boolean,
    val updatedAt: Long
) {
    val isUnplayable: Boolean
        get() = scanState == ScanState.UNPLAYABLE ||
            metadataState == MetadataState.UNPLAYABLE ||
            playableState == PlayableState.UNPLAYABLE
}
