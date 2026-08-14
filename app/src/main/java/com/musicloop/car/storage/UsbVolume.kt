package com.musicloop.car.storage

/**
 * A mounted removable volume. Paths are filesystem locations for read-only access.
 */
data class UsbVolume(
    val uuid: String?,
    val label: String?,
    val absolutePath: String,
    val state: String,
    val isRemovable: Boolean
) {
    val identity: String?
        get() = uuid?.takeIf { it.isNotBlank() } ?: MusicFolderPaths.volumeIdentityFromPath(absolutePath)
}
