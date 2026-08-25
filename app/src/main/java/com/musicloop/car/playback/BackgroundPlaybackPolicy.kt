package com.musicloop.car.playback

/**
 * Audio-only foreground session policy. Video keeps using the in-app PlayerView
 * and must not hold a background MediaSessionService.
 */
object BackgroundPlaybackPolicy {
    fun shouldHoldService(state: PlaybackUiState): Boolean {
        if (state.mode != PlayerMode.AUDIO) {
            return false
        }
        if (state.current == null || state.current.mediaType != "AUDIO") {
            return false
        }
        if (state.needsPrepare) {
            return false
        }
        return when (state.status) {
            PlayStatus.PLAYING, PlayStatus.PAUSED, PlayStatus.BUFFERING, PlayStatus.ENDED -> true
            PlayStatus.IDLE, PlayStatus.STOPPED, PlayStatus.ERROR -> false
        }
    }
}
