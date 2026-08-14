package com.musicloop.car.storage

import android.content.Context

/**
 * Persists the selected music folder in internal SharedPreferences.
 * Never writes to USB.
 */
class MusicFolderStore(context: Context) : MusicFolderRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): MusicFolderRecord? {
        val absolute = prefs.getString(KEY_ABSOLUTE, null) ?: return null
        if (absolute.isBlank()) {
            return null
        }
        return MusicFolderRecord(
            absolutePath = absolute,
            relativePath = prefs.getString(KEY_RELATIVE, "") ?: "",
            volumeUuid = prefs.getString(KEY_VOLUME_UUID, null),
            volumeLabel = prefs.getString(KEY_VOLUME_LABEL, null),
            folderName = prefs.getString(KEY_FOLDER_NAME, "") ?: ""
        )
    }

    override fun save(record: MusicFolderRecord) {
        prefs.edit()
            .putString(KEY_ABSOLUTE, record.absolutePath)
            .putString(KEY_RELATIVE, record.relativePath)
            .putString(KEY_VOLUME_UUID, record.volumeUuid)
            .putString(KEY_VOLUME_LABEL, record.volumeLabel)
            .putString(KEY_FOLDER_NAME, record.folderName)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "music_folder"
        private const val KEY_ABSOLUTE = "absolute_path"
        private const val KEY_RELATIVE = "relative_path"
        private const val KEY_VOLUME_UUID = "volume_uuid"
        private const val KEY_VOLUME_LABEL = "volume_label"
        private const val KEY_FOLDER_NAME = "folder_name"
    }
}
