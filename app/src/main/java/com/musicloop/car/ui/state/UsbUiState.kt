package com.musicloop.car.ui.state

enum class UsbStatus {
    WAITING_FOR_USB,
    SCANNING_USB,
    USB_READY,
    USB_ERROR,
    USB_DISCONNECTED,
    FOLDER_NOT_FOUND,
    NEEDS_FOLDER
}

data class UsbUiState(
    val status: UsbStatus,
    val musicFolderLabel: String? = null,
    val resolvedAbsolutePath: String? = null,
    val hasSavedFolder: Boolean = false,
    val usbPresent: Boolean = false,
    val volumeIdentity: String? = null,
    val volumeRootPath: String? = null
) {
    val folderButtonIsChange: Boolean
        get() = hasSavedFolder
}
