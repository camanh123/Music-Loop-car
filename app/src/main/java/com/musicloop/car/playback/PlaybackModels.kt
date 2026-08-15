package com.musicloop.car.playback

enum class PlayStatus {
    IDLE,
    PLAYING,
    PAUSED,
    BUFFERING,
    ENDED,
    STOPPED,
    ERROR
}

enum class PlayerMode {
    IDLE,
    AUDIO,
    VIDEO
}

data class PlayableRef(
    val id: Long,
    val volumeId: String,
    val relativePath: String,
    val fileName: String,
    val mediaType: String,
    val title: String?,
    val artist: String?
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: fileName

    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() } ?: ""
}

data class PlaybackUiState(
    val status: PlayStatus = PlayStatus.IDLE,
    val mode: PlayerMode = PlayerMode.IDLE,
    val current: PlayableRef? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

sealed class ResolveResult {
    data class Ready(val absolutePath: String, val rootPath: String) : ResolveResult()
    data class Offline(val volumeId: String) : ResolveResult()
    data class Missing(val absolutePath: String) : ResolveResult()
    data class Unsupported(val reason: String) : ResolveResult()
    data class Invalid(val reason: String) : ResolveResult()
}

interface PlaybackEngine {
    fun prepareAndPlay(absolutePath: String)
    fun pause()
    fun play()
    fun stop()
    fun seekTo(positionMs: Long)
    fun position(): Long
    fun duration(): Long
    fun isPlaying(): Boolean
    fun release()
}
