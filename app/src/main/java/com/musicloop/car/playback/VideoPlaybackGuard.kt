package com.musicloop.car.playback

object VideoPlaybackGuard {
    fun shouldExitForUsbLoss(requestedRelativePath: String?, state: PlaybackUiState): Boolean {
        if (requestedRelativePath.isNullOrBlank()) {
            return false
        }
        if (state.current?.relativePath != requestedRelativePath) {
            return false
        }
        return state.errorMessage == "USB disconnected" || state.errorMessage == "USB offline"
    }
}
