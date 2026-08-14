package com.musicloop.car.player

/**
 * Playback engine abstraction so UI never talks to MediaPlayer directly.
 * A later phase can replace MediaPlayerEngine without rewriting the UI.
 */
interface PlayerEngine {
    fun setListener(listener: PlayerEngineListener?)
    fun prepare(absolutePath: String)
    fun start()
    fun pause()
    fun seekTo(positionMs: Int)
    fun stop()
    fun reset()
    fun release()
    fun currentPosition(): Int
    fun duration(): Int
    fun isPlaying(): Boolean
}

interface PlayerEngineListener {
    fun onPrepared(durationMs: Int)
    fun onCompletion()
    fun onError(error: PlaybackError)
}

fun interface TrackFileAccess {
    fun resolveReadable(volumeRoot: String?, relativePath: String): String?
}

interface PlaybackStateRepository {
    fun load(): SavedPlaybackState?
    fun save(state: SavedPlaybackState)
    fun loadRepeatMode(): RepeatMode
    fun saveRepeatMode(mode: RepeatMode)
    fun loadShuffle(): Boolean = false
    fun saveShuffle(enabled: Boolean) = Unit
    fun loadQueueSnapshot(): SavedQueueState? = null
    fun saveQueueSnapshot(state: SavedQueueState?) = Unit
}

interface AudioFocusGate {
    fun requestFocus(): Boolean
    fun abandonFocus()
}

object NoOpAudioFocusGate : AudioFocusGate {
    override fun requestFocus(): Boolean = true
    override fun abandonFocus() = Unit
}
