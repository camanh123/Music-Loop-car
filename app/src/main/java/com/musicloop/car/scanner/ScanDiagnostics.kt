package com.musicloop.car.scanner

/**
 * Read-only CARFU scan summary. Paths and counts only — never file contents.
 */
data class ScanDiagnostics(
    val volumeIdentity: String = "",
    val volumeRoot: String = "",
    val folderAbsolute: String = "",
    val folderExists: Boolean = false,
    val folderIsDirectory: Boolean = false,
    val folderCanRead: Boolean = false,
    val totalFilesystemEntries: Int = 0,
    val audioCandidates: Int = 0,
    val acceptedAudioFiles: Int = 0,
    val rejectedFiles: Int = 0,
    val metadataOk: Int = 0,
    val metadataFailed: Int = 0,
    val rowsBefore: Int = 0,
    val rowsInsertedOrUpdated: Int = 0,
    val rowsAfter: Int = 0,
    val libraryCount: Int = 0
) {
    fun formatSummary(): String {
        return buildString {
            append("USB:\n ")
            append(volumeRoot.ifBlank { "—" })
            append("\n\nMusic folder:\n ")
            append(folderAbsolute.ifBlank { "—" })
            append("\n\nFolder exists:\n ")
            append(yesNo(folderExists))
            append("\n\nEntries:\n ")
            append(totalFilesystemEntries)
            append("\n\nAudio candidates:\n ")
            append(audioCandidates)
            append("\n\nIndexed:\n ")
            append(acceptedAudioFiles)
            append("\n\nMetadata OK:\n ")
            append(metadataOk)
            append("\n\nMetadata failed:\n ")
            append(metadataFailed)
            append("\n\nLibrary:\n ")
            append(libraryCount)
        }
    }

    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"
}

data class EnumerationResult(
    val files: List<DiscoveredFile>,
    val totalFilesystemEntries: Int = 0,
    val audioCandidates: Int = 0,
    val acceptedAudioFiles: Int = files.size,
    val rejectedFiles: Int = 0,
    val folderExists: Boolean = true,
    val folderIsDirectory: Boolean = true,
    val folderCanRead: Boolean = true,
    val folderAbsolutePath: String = "",
    val rootUnreadable: Boolean = false
)
