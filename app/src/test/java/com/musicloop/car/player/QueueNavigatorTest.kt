package com.musicloop.car.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueNavigatorTest {

    @Test
    fun previousRestartsWhenPositionIsPastFiveSeconds() {
        assertEquals(PreviousAction.RESTART, QueueNavigator.previousAction(5001))
        assertEquals(PreviousAction.PREVIOUS, QueueNavigator.previousAction(5000))
        assertEquals(PreviousAction.PREVIOUS, QueueNavigator.previousAction(0))
    }

    @Test
    fun nextWithoutWrapEndsAtLibraryEnd() {
        val result = QueueNavigator.nextIndex(size = 3, fromIndex = 2, wrap = false)
        assertTrue(result.ended)
        assertNull(result.index)
    }

    @Test
    fun nextWithWrapReturnsFirstIndex() {
        val result = QueueNavigator.nextIndex(size = 3, fromIndex = 2, wrap = true)
        assertEquals(0, result.index)
        assertFalse(result.ended)
    }

    @Test
    fun previousWithWrapReturnsLastIndex() {
        val result = QueueNavigator.previousIndex(size = 3, fromIndex = 0, wrap = true)
        assertEquals(2, result.index)
    }

    @Test
    fun emptyQueueIsExhausted() {
        val result = QueueNavigator.findPlayable(0, 0, 1, wrap = true) { true }
        assertTrue(result.exhausted)
    }

    @Test
    fun findPlayableSkipsRejectedIndices() {
        val result = QueueNavigator.findPlayable(
            size = 4,
            fromIndex = 0,
            direction = 1,
            wrap = true
        ) { index -> index == 3 }
        assertEquals(3, result.index)
    }

    @Test
    fun findPlayableExhaustsWhenNothingCanPlay() {
        val result = QueueNavigator.findPlayable(
            size = 3,
            fromIndex = 1,
            direction = 1,
            wrap = true
        ) { false }
        assertTrue(result.exhausted)
    }

    @Test
    fun completionDoesNotWrapPastEnd() {
        val result = QueueNavigator.findPlayable(
            size = 2,
            fromIndex = 1,
            direction = 1,
            wrap = false
        ) { true }
        assertTrue(result.ended)
    }

    @Test
    fun oneTrackNextWithWrapReturnsSameIndex() {
        val result = QueueNavigator.findPlayable(
            size = 1,
            fromIndex = 0,
            direction = 1,
            wrap = true
        ) { true }
        assertEquals(0, result.index)
    }
}
