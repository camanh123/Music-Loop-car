package com.musicloop.car.library

data class MediaIdentity(
    val volumeId: String,
    val relativePath: String
) {
    val fileName: String get() = relativePath.substringAfterLast('/').ifBlank { relativePath }
}

fun MediaListRow.identity(): MediaIdentity = MediaIdentity(volumeId, relativePath)
