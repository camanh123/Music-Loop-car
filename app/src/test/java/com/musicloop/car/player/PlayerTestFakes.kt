package com.musicloop.car.player

class FakePlayerEngine : PlayerEngine {
    var engineListener: PlayerEngineListener? = null
    var lastPath: String? = null
    var playing: Boolean = false
    var positionMs: Int = 0
    var durationMs: Int = 180_000
    var released: Boolean = false
    var stopped: Boolean = false
    var failPrepare: Boolean = false
    var autoPrepare: Boolean = true
    var startCount: Int = 0

    override fun setListener(listener: PlayerEngineListener?) {
        this.engineListener = listener
    }

    override fun prepare(absolutePath: String) {
        lastPath = absolutePath
        released = false
        stopped = false
        if (failPrepare) {
            engineListener?.onError(PlaybackErrors.prepareFailed("prepare failed"))
            return
        }
        if (autoPrepare) {
            engineListener?.onPrepared(durationMs)
        }
    }

    override fun start() {
        playing = true
        startCount++
    }

    override fun pause() {
        playing = false
    }

    override fun seekTo(positionMs: Int) {
        this.positionMs = positionMs
    }

    override fun stop() {
        playing = false
        stopped = true
    }

    override fun reset() {
        playing = false
    }

    override fun release() {
        playing = false
        released = true
    }

    override fun currentPosition(): Int = positionMs

    override fun duration(): Int = durationMs

    override fun isPlaying(): Boolean = playing

    fun complete() {
        playing = false
        engineListener?.onCompletion()
    }

    fun error(error: PlaybackError) {
        playing = false
        released = true
        engineListener?.onError(error)
    }
}

class InMemoryPlaybackStore : PlaybackStateRepository {
    var saved: SavedPlaybackState? = null
    var repeatMode: RepeatMode = RepeatMode.OFF

    override fun load(): SavedPlaybackState? = saved

    override fun save(state: SavedPlaybackState) {
        saved = state
    }

    override fun loadRepeatMode(): RepeatMode = repeatMode

    override fun saveRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }
}

class MapFileAccess(private val readable: MutableSet<String>) : TrackFileAccess {
    override fun resolveReadable(volumeRoot: String?, relativePath: String): String? {
        if (volumeRoot.isNullOrBlank()) {
            return null
        }
        return if (relativePath in readable) "$volumeRoot/$relativePath" else null
    }

    fun remove(relativePath: String) {
        readable.remove(relativePath)
    }
}
