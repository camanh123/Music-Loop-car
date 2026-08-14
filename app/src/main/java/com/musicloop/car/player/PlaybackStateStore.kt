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
        return when (prefs.getString(KEY_REPEAT, RepeatMode.OFF.name)) {
            RepeatMode.ONE.name -> RepeatMode.ONE
            RepeatMode.ALL.name -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
    }

    override fun saveRepeatMode(mode: RepeatMode) {
        prefs.edit().putString(KEY_REPEAT, mode.name).apply()
    }

    override fun loadShuffle(): Boolean {
        return prefs.getBoolean(KEY_SHUFFLE, false)
    }

    override fun saveShuffle(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    override fun loadQueueSnapshot(): SavedQueueState? {
        val raw = prefs.getString(KEY_QUEUE_PATHS, null) ?: return null
        val source = try {
            QueueSource.valueOf(prefs.getString(KEY_QUEUE_SOURCE, QueueSource.ALL_SONGS.name) ?: QueueSource.ALL_SONGS.name)
        } catch (_: Exception) {
            QueueSource.ALL_SONGS
        }
        val playlistId = prefs.getLong(KEY_PLAYLIST_ID, -1L).takeIf { it > 0 }
        return SavedQueueState(
            source = source,
            playlistId = playlistId,
            relativePaths = raw.split('\u001f').filter { it.isNotBlank() },
            shuffled = prefs.getBoolean(KEY_SHUFFLE, false)
        )
    }

    override fun saveQueueSnapshot(state: SavedQueueState?) {
        if (state == null) {
            prefs.edit()
                .remove(KEY_QUEUE_PATHS)
                .remove(KEY_QUEUE_SOURCE)
                .remove(KEY_PLAYLIST_ID)
                .apply()
            return
        }
        prefs.edit()
            .putString(KEY_QUEUE_PATHS, state.relativePaths.joinToString("\u001f"))
            .putString(KEY_QUEUE_SOURCE, state.source.name)
            .putLong(KEY_PLAYLIST_ID, state.playlistId ?: -1L)
            .apply()
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
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_QUEUE_PATHS = "queue_paths"
        private const val KEY_QUEUE_SOURCE = "queue_source"
        private const val KEY_PLAYLIST_ID = "playlist_id"
    }
}
