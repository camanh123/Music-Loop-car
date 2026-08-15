package com.musicloop.car.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSaveGateTest {
    @Test
    fun throttlesRapidPositionTicks() {
        val gate = PositionSaveGate(minIntervalMs = 5_000L, minDeltaMs = 1_000L)
        assertTrue(gate.shouldSave(0L, 1_000L, force = false))
        assertFalse(gate.shouldSave(400L, 1_400L, force = false))
        assertFalse(gate.shouldSave(2_000L, 3_000L, force = false))
        assertTrue(gate.shouldSave(5_000L, 4_000L, force = false))
    }

    @Test
    fun forceSaveIgnoresThrottle() {
        val gate = PositionSaveGate(minIntervalMs = 5_000L, minDeltaMs = 1_000L)
        assertTrue(gate.shouldSave(0L, 1_000L, force = false))
        assertTrue(gate.shouldSave(100L, 1_100L, force = true))
    }
}
