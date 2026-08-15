package com.musicloop.car.usb

/**
 * USB host/library state. StorageManager snapshots are the only source of truth
 * for whether Android has actually mounted a removable volume.
 */
enum class UsbHostState {
    USB_ONLINE,
    USB_OFFLINE,
    USB_NOT_DETECTED,
    USB_SCANNING,
    USB_READY,
    USB_ERROR
}

object UsbPresenceClassifier {
    fun classify(
        removableMounted: Int,
        scanning: Boolean,
        scanFailed: Boolean,
        scanCompleted: Boolean,
        hadKnownVolume: Boolean
    ): UsbHostState {
        if (removableMounted <= 0) {
            return if (hadKnownVolume) UsbHostState.USB_OFFLINE else UsbHostState.USB_NOT_DETECTED
        }
        if (scanFailed) {
            return UsbHostState.USB_ERROR
        }
        if (scanning) {
            return UsbHostState.USB_SCANNING
        }
        if (scanCompleted) {
            return UsbHostState.USB_READY
        }
        return UsbHostState.USB_ONLINE
    }
}

object UsbRecoveryPolicy {
    /** StorageManager-only retry while the UI is in the foreground. 3–5s range. */
    const val POLL_INTERVAL_MS = 4_000L
    const val ANDROID_USB_NOT_DETECTED = "ANDROID_USB_NOT_DETECTED"
    const val CONNECTED_UPDATING = "USB đã kết nối — đang cập nhật thư viện..."
    const val NOT_DETECTED_USER =
        "USB chưa được Android nhận diện.\nHãy kiểm tra/cắm lại USB hoặc thử cổng USB khác."
    const val NOT_DETECTED_OS =
        "CARFU/Android hiện chưa nhận USB.\nMusicLoop không thể mount USB ở cấp ứng dụng."
}

data class IncrementalScanReport(
    val volumeId: String = "",
    val changed: Int = 0,
    val newItems: Int = 0,
    val stale: Int = 0,
    val unchanged: Int = 0
)
