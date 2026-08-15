package com.musicloop.car.library

enum class ScanUiState {
    IDLE,
    DETECTING_USB,
    SCANNING,
    COMPLETED,
    FAILED,
    USB_OFFLINE
}

data class ScanProgress(
    val scanned: Int = 0,
    val total: Int = 0,
    val currentName: String = ""
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((scanned.toLong() * 100L) / total.toLong()).toInt().coerceIn(0, 100)
}

enum class ScanOutcome {
    COMPLETED,
    CANCELLED,
    VOLUME_OFFLINE,
    FAILED
}

data class MediaListRow(
    val id: Long,
    val volumeId: String,
    val relativePath: String,
    val fileName: String,
    val extension: String,
    val mediaType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val title: String?,
    val artist: String?,
    val scanStatus: String
)

data class LibraryUiState(
    val scanState: ScanUiState = ScanUiState.IDLE,
    val usbOnline: Boolean = false,
    val volumeDescription: String = "",
    val volumeId: String = "",
    val lastKnownRootPath: String? = null,
    val progress: ScanProgress = ScanProgress(),
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val totalCount: Int = 0,
    val media: List<MediaListRow> = emptyList(),
    val statusMessage: String = "",
    val errorMessage: String? = null
)
