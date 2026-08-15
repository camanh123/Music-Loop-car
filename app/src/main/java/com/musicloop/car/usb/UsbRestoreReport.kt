package com.musicloop.car.usb

data class UsbRestoreReport(
    val volumeId: String,
    val cachedItems: Int,
    val libraryVisibleMs: Long,
    val scanCompletedMs: Long? = null,
    val restoreStartedAtElapsed: Long = 0L,
)
