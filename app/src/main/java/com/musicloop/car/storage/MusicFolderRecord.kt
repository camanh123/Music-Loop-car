package com.musicloop.car.storage

/**
 * Remembered music folder. Stored only in internal app storage.
 */
data class MusicFolderRecord(
    val absolutePath: String,
    val relativePath: String,
    val volumeUuid: String?,
    val volumeLabel: String?,
    val folderName: String
) {
    fun displayLabel(): String = MusicFolderPaths.displayMusicFolder(volumeLabel, relativePath)

    companion object {
        fun fromSelection(absolutePath: String, volume: UsbVolume?): MusicFolderRecord {
            val relative = if (volume != null) {
                MusicFolderPaths.relativeToVolume(volume.absolutePath, absolutePath) ?: ""
            } else {
                ""
            }
            return MusicFolderRecord(
                absolutePath = MusicFolderPaths.normalizeAbsolute(absolutePath),
                relativePath = relative,
                volumeUuid = volume?.identity,
                volumeLabel = volume?.label ?: "USB",
                folderName = MusicFolderPaths.folderName(relative).ifEmpty {
                    volume?.label ?: "USB"
                }
            )
        }
    }
}
