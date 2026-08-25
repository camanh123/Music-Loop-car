package com.musicloop.car.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase2CSystemMediaAuditTest {

    @Test
    fun exoPlayerUsesStandardFocusAndBecomingNoisyWithoutCustomAudioManager() {
        val roots = listOf(
            File("src/main/java"),
            File("../app/src/main/java")
        )
        val srcRoot = roots.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("Could not locate production source root")
        val manager = srcRoot.resolve("com/musicloop/car/playback/Media3PlayerManager.kt")
        val text = manager.readText()
        assertTrue(text.contains("setHandleAudioBecomingNoisy(true)"))
        assertTrue(text.contains("handleAudioFocus="))
        assertTrue(text.contains("setAudioAttributes("))
        assertEquals(1, Regex("ExoPlayer\\.Builder").findAll(text).count())
        assertFalse(text.contains("requestAudioFocus"))
        assertFalse(text.contains("abandonAudioFocus"))
        assertFalse(text.contains("android.media.AudioManager"))
        assertTrue(text.contains("onPlayWhenReadyChanged"))
        assertTrue(text.contains("reconcileUiFromPlayer"))
    }

    @Test
    fun forwardingPlayerRoutesSessionTransportToCoordinator() {
        val roots = listOf(
            File("src/main/java"),
            File("../app/src/main/java")
        )
        val srcRoot = roots.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("Could not locate production source root")
        val text = srcRoot.resolve("com/musicloop/car/playback/CoordinatorForwardingPlayer.kt").readText()
        assertTrue(text.contains("coordinator.resume()"))
        assertTrue(text.contains("coordinator.pause()"))
        assertTrue(text.contains("coordinator.seekTo"))
        assertTrue(text.contains("coordinator.skipToNext()"))
        assertTrue(text.contains("coordinator.skipToPrevious()"))
        assertTrue(text.contains("setPlayWhenReady"))
    }
}
