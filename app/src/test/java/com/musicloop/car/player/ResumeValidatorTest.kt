package com.musicloop.car.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeValidatorTest {

    @Test
    fun sameTrackRequiresMatchingVolumeAndRelativePath() {
        assertTrue(
            ResumeValidator.sameTrack("AAAA-AAAA", "Music/song.mp3", "AAAA-AAAA", "Music/song.mp3")
        )
        assertFalse(
            ResumeValidator.sameTrack("AAAA-AAAA", "Music/song.mp3", "BBBB-BBBB", "Music/song.mp3")
        )
        assertFalse(
            ResumeValidator.sameTrack("AAAA-AAAA", "Music/song.mp3", "AAAA-AAAA", "Music/other.mp3")
        )
    }

    @Test
    fun invalidSavedPositionStartsAtZero() {
        assertEquals(0, ResumeValidator.clampPosition(-1, 180_000))
        assertEquals(0, ResumeValidator.clampPosition(180_000, 180_000))
        assertEquals(0, ResumeValidator.clampPosition(200_000, 180_000))
        assertEquals(0, ResumeValidator.clampPosition(84_000, null as Int?))
        assertEquals(0, ResumeValidator.clampPosition(84_000, 0))
    }

    @Test
    fun validSavedPositionIsKept() {
        assertEquals(84_000, ResumeValidator.clampPosition(84_000, 180_000))
        assertEquals(0, ResumeValidator.clampPosition(0, 180_000))
        assertEquals(179_999, ResumeValidator.clampPosition(179_999, 180_000))
    }

    @Test
    fun changedTrackMustStartFromZero() {
        val same = ResumeValidator.sameTrack(
            "AAAA-AAAA",
            "Music/old.mp3",
            "AAAA-AAAA",
            "Music/new.mp3"
        )
        assertFalse(same)
        assertEquals(0, ResumeValidator.clampPosition(84_000, if (same) 180_000 else 0))
    }
}
