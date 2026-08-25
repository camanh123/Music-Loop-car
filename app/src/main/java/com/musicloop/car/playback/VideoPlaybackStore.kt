package com.musicloop.car.playback

import android.content.Context

/**
 * SharedPreferences-backed video restore state. Avoids a Room schema change.
 * Never stores USB mount paths such as a runtime /storage volume root.
 */
class VideoPlaybackStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): VideoPlaybackState {
        return VideoPlaybackState(
            volumeId = prefs.getString(KEY_VOLUME_ID, "").orEmpty(),
            relativePath = prefs.getString(KEY_RELATIVE_PATH, "").orEmpty(),
            fileName = prefs.getString(KEY_FILE_NAME, "").orEmpty(),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L),
            listPosition = prefs.getInt(KEY_LIST_POSITION, 0).coerceAtLeast(0),
            listOffset = prefs.getInt(KEY_LIST_OFFSET, 0),
            playWhenReady = prefs.getBoolean(KEY_PLAY_WHEN_READY, true),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    @Synchronized
    fun saveIdentity(
        volumeId: String,
        relativePath: String,
        fileName: String,
        title: String?,
        nowMs: Long,
        resetPosition: Boolean
    ) {
        val editor = prefs.edit()
            .putString(KEY_VOLUME_ID, volumeId)
            .putString(KEY_RELATIVE_PATH, relativePath)
            .putString(KEY_FILE_NAME, fileName)
            .putString(KEY_TITLE, title.orEmpty())
            .putLong(KEY_UPDATED_AT, nowMs)
        if (resetPosition) {
            editor.putLong(KEY_POSITION_MS, 0L)
        }
        editor.apply()
    }

    @Synchronized
    fun savePosition(positionMs: Long, playWhenReady: Boolean, nowMs: Long) {
        prefs.edit()
            .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
            .putBoolean(KEY_PLAY_WHEN_READY, playWhenReady)
            .putLong(KEY_UPDATED_AT, nowMs)
            .apply()
    }

    @Synchronized
    fun saveListScroll(listPosition: Int, listOffset: Int, nowMs: Long) {
        prefs.edit()
            .putInt(KEY_LIST_POSITION, listPosition.coerceAtLeast(0))
            .putInt(KEY_LIST_OFFSET, listOffset)
            .putLong(KEY_UPDATED_AT, nowMs)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "video_playback_state"
        private const val KEY_VOLUME_ID = "volume_id"
        private const val KEY_RELATIVE_PATH = "relative_path"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_TITLE = "title"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_LIST_POSITION = "list_position"
        private const val KEY_LIST_OFFSET = "list_offset"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
