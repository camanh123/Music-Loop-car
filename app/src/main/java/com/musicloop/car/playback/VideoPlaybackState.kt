package com.musicloop.car.playback

/**
 * Persistent video UX state. Identity is volumeId + relativePath, never a mount path.
 */
data class VideoPlaybackState(
    val volumeId: String = "",
    val relativePath: String = "",
    val fileName: String = "",
    val title: String = "",
    val positionMs: Long = 0L,
    val listPosition: Int = 0,
    val listOffset: Int = 0,
    val playWhenReady: Boolean = true,
    val updatedAt: Long = 0L
) {
    val hasIdentity: Boolean
        get() = volumeId.isNotBlank() && relativePath.isNotBlank()
}
