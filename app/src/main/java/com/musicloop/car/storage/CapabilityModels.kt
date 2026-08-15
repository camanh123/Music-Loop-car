package com.musicloop.car.storage

data class DeviceInfo(
    val brand: String,
    val model: String,
    val sdkInt: Int,
    val hardware: String
)

data class VerificationChecks(
    val volumeDetected: Boolean,
    val rootResolved: Boolean,
    val directoryReadable: Boolean,
    val mediaFilesReadable: Boolean
) {
    companion object {
        fun evaluate(
            volumePresent: Boolean,
            rootPath: String?,
            exists: Boolean,
            isDirectory: Boolean,
            canRead: Boolean,
            listFilesNonNull: Boolean,
            mediaFilesReadable: Boolean
        ): VerificationChecks {
            val rootResolved = !rootPath.isNullOrBlank()
            val directoryReadable = rootResolved &&
                exists &&
                isDirectory &&
                canRead &&
                listFilesNonNull
            return VerificationChecks(
                volumeDetected = volumePresent,
                rootResolved = rootResolved,
                directoryReadable = directoryReadable,
                mediaFilesReadable = mediaFilesReadable
            )
        }
    }
}

data class MediaReadSample(
    val absolutePath: String,
    val kind: MediaKind,
    val sizeBytes: Long,
    val streamReadPass: Boolean
)

data class MediaScanResult(
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val audioByExtension: Map<String, Int> = emptyMap(),
    val videoByExtension: Map<String, Int> = emptyMap(),
    val samples: List<MediaReadSample> = emptyList(),
    val scanned: Boolean = false,
    val skipReason: String? = null
) {
    val mediaFilesReadable: Boolean
        get() = samples.any { it.streamReadPass }
}

data class VolumeReport(
    val index: Int,
    val description: String,
    val state: String,
    val removableCandidate: Boolean,
    val isPrimary: Boolean,
    val uuid: String?,
    val rootPath: String?,
    val exists: Boolean,
    val canRead: Boolean,
    val isDirectory: Boolean,
    val listFilesNonNull: Boolean,
    val totalSpaceBytes: Long,
    val freeSpaceBytes: Long,
    val checks: VerificationChecks,
    val media: MediaScanResult
)
