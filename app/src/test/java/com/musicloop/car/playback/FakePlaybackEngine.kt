package com.musicloop.car.playback

class FakePlaybackEngine : PlaybackEngine {
    var preparedPath: String? = null
    var playing = false
    var stopped = false
    var released = false
    var positionMs = 0L
    var durationMs = 12_000L
    val seeks = mutableListOf<Long>()

    override fun prepareAndPlay(absolutePath: String) {
        preparedPath = absolutePath
        playing = true
        stopped = false
        positionMs = 0L
    }

    override fun pause() {
        playing = false
    }

    override fun play() {
        playing = true
        stopped = false
    }

    override fun stop() {
        playing = false
        stopped = true
        positionMs = 0L
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
        this.positionMs = positionMs
    }

    override fun position(): Long = positionMs

    override fun duration(): Long = durationMs

    override fun isPlaying(): Boolean = playing

    override fun release() {
        released = true
        playing = false
    }
}
