package com.musicloop.car.player

enum class PlayerState {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR,
    USB_DISCONNECTED
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class QueueSource {
    ALL_SONGS,
    FAVORITES,
    PLAYLIST
}

data class SavedQueueState(
    val source: QueueSource = QueueSource.ALL_SONGS,
    val playlistId: Long? = null,
    val relativePaths: List<String> = emptyList(),
    val shuffled: Boolean = false
)

enum class PlaybackMessage {
    NONE,
    USB_DISCONNECTED,
    CANNOT_PLAY_FILE,
    NO_PLAYABLE_TRACK,
    FILE_MISSING,
    PREPARING
}

enum class PlaybackErrorKind {
    UNKNOWN,
    SERVER_DIED,
    IO,
    MALFORMED,
    UNSUPPORTED,
    TIMED_OUT,
    PREPARE_FAILED,
    MISSING_FILE
}

data class PlaybackError(
    val kind: PlaybackErrorKind,
    val what: Int = 0,
    val extra: Int = 0,
    val detail: String? = null
)

data class SavedPlaybackState(
    val volumeIdentity: String,
    val relativePath: String,
    val positionMs: Int,
    val updatedAt: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Int = 0
)

data class PlaybackSnapshot(
    val state: PlayerState = PlayerState.IDLE,
    val track: QueueItem? = null,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val queueSource: QueueSource = QueueSource.ALL_SONGS,
    val playlistId: Long? = null,
    val message: PlaybackMessage = PlaybackMessage.NONE,
    val error: PlaybackError? = null
) {
    val favorite: Boolean
        get() = track?.favorite == true
    val isPlaying: Boolean
        get() = state == PlayerState.PLAYING

    val canSeek: Boolean
        get() = durationMs > 0 &&
            state != PlayerState.USB_DISCONNECTED &&
            state != PlayerState.PREPARING &&
            track != null
}
