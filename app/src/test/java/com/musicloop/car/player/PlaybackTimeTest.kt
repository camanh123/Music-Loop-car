package com.musicloop.car.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {

    @Test
    fun formatsAsMmSsWithLeadingZeros() {
        assertEquals("00:00", PlaybackTime.format(0))
        assertEquals("01:24", PlaybackTime.format(84_000))
        assertEquals("04:37", PlaybackTime.format(277_000))
        assertEquals("84:00", PlaybackTime.format(84 * 60 * 1000))
    }
}
