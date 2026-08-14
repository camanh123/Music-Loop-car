package com.musicloop.car.player

import android.content.Context

/**
 * Internal-only playback resume state. Never writes to USB.
 */
class PlaybackStateStore(context: Context) : PlaybackStateRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): SavedPlaybackState? {
        val volume = prefs.getString(KEY_VOLUME, null) ?: return null
        val relative = prefs.getString(KEY_RELATIVE, null) ?: return null
        if (volume.isBlank() || relative.isBlank()) {
            return null
        }
        return SavedPlaybackState(
            volumeIdentity = volume,
            relativePath = relative,
            positionMs = prefs.getInt(KEY_POSITION, 0),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
            title = prefs.getString(KEY_TITLE, "") ?: "",
            artist = prefs.getString(KEY_ARTIST, "") ?: "",
            album = prefs.getString(KEY_ALBUM, "") ?: "",
            durationMs = prefs.getInt(KEY_DURATION, 0)
        )
    }

    override fun save(state: SavedPlaybackState) {
        prefs.edit()
            .putString(KEY_VOLUME, state.volumeIdentity)
            .putString(KEY_RELATIVE, state.relativePath)
            .putInt(KEY_POSITION, state.positionMs)
            .putLong(KEY_UPDATED_AT, state.updatedAt)
            .putString(KEY_TITLE, state.title)
            .putString(KEY_ARTIST, state.artist)
            .putString(KEY_ALBUM, state.album)
            .putInt(KEY_DURATION, state.durationMs)
            .apply()
    }

    override fun loadRepeatMode(): RepeatMode {
        return if (prefs.getString(KEY_REPEAT, RepeatMode.OFF.name) == RepeatMode.ONE.name) {
            RepeatMode.ONE
        } else {
            RepeatMode.OFF
        }
    }

    override fun saveRepeatMode(mode: RepeatMode) {
        prefs.edit().putString(KEY_REPEAT, mode.name).apply()
    }

    companion object {
        const val PREFS_NAME = "playback_state"
        private const val KEY_VOLUME = "volume_identity"
        private const val KEY_RELATIVE = "relative_path"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ALBUM = "album"
        private const val KEY_DURATION = "duration_ms"
        private const val KEY_REPEAT = "repeat_mode"
    }
}
