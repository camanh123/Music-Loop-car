package com.musicloop.car.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPlaybackPolicyTest {
    @Test
    fun holdsServiceOnlyForActiveAudio() {
        val audio = PlaybackUiState(
            status = PlayStatus.PLAYING,
            mode = PlayerMode.AUDIO,
            current = PlayableRef(
                id = 1L,
                volumeId = "VOL",
                relativePath = "a.mp3",
                fileName = "a.mp3",
                mediaType = "AUDIO",
                title = "A",
                artist = "Artist"
            )
        )
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(audio))
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(audio.copy(status = PlayStatus.PAUSED)))
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(audio.copy(status = PlayStatus.BUFFERING)))
        assertFalse(BackgroundPlaybackPolicy.shouldHoldService(audio.copy(status = PlayStatus.STOPPED)))
        assertFalse(BackgroundPlaybackPolicy.shouldHoldService(audio.copy(status = PlayStatus.ERROR)))
        assertFalse(
            BackgroundPlaybackPolicy.shouldHoldService(
                audio.copy(status = PlayStatus.PAUSED, needsPrepare = true)
            )
        )
    }

    @Test
    fun doesNotHoldServiceForVideo() {
        val video = PlaybackUiState(
            status = PlayStatus.PLAYING,
            mode = PlayerMode.VIDEO,
            current = PlayableRef(
                id = 2L,
                volumeId = "VOL",
                relativePath = "clip.mp4",
                fileName = "clip.mp4",
                mediaType = "VIDEO",
                title = "Clip",
                artist = null
            )
        )
        assertFalse(BackgroundPlaybackPolicy.shouldHoldService(video))
    }
}
