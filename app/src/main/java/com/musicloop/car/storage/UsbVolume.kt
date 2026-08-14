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
    /**
     * FAT/StorageVolume UUID when the firmware exposes one.
     * Null on typical CARFU `/storage/USB1` mounts that have no UUID.
     */
    val identity: String?
        get() = uuid?.takeIf { it.isNotBlank() } ?: MusicFolderPaths.volumeIdentityFromPath(absolutePath)

    /**
     * Identity used for Room rows and MusicSession scans.
     *
     * CARFU UIS7862 often mounts as `/storage/USB1` with a null UUID.
     * A null identity previously prevented the scanner from starting, which
     * left SONGS empty even after the user selected a music folder.
     */
    fun stableIdentity(): String {
        return identity ?: UNLABELED_USB_IDENTITY
    }

    companion object {
        const val UNLABELED_USB_IDENTITY = "unlabeled-usb"
    }
}
