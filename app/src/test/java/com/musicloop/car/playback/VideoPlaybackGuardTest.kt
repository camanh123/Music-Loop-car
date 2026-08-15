package com.musicloop.car.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackGuardTest {
    @Test
    fun ignoresStaleUsbErrorFromADifferentItem() {
        val state = PlaybackUiState(
            current = PlayableRef(
                id = 1L,
                volumeId = "AAAA-AAAA",
                relativePath = "old.mp3",
                fileName = "old.mp3",
                mediaType = "AUDIO",
                title = "old",
                artist = null
            ),
            errorMessage = "USB disconnected"
        )
        assertFalse(VideoPlaybackGuard.shouldExitForUsbLoss("clip.mp4", state))
    }

    @Test
    fun exitsOnlyWhenRequestedVideoMatchesUsbLoss() {
        val state = PlaybackUiState(
            current = PlayableRef(
                id = 2L,
                volumeId = "AAAA-AAAA",
                relativePath = "clip.mp4",
                fileName = "clip.mp4",
                mediaType = "VIDEO",
                title = "clip",
                artist = null
            ),
            errorMessage = "USB offline"
        )
        assertTrue(VideoPlaybackGuard.shouldExitForUsbLoss("clip.mp4", state))
        assertFalse(VideoPlaybackGuard.shouldExitForUsbLoss(null, state))
        assertFalse(VideoPlaybackGuard.shouldExitForUsbLoss("", state.copy(errorMessage = null)))
    }
}
