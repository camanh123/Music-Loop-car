package com.musicloop.car.playback

import com.musicloop.car.library.MediaIdentity
import com.musicloop.car.storage.VolumeSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    @Test
    fun playsResolvedAudioFromUsbPath() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine, onlineRoot = "/mnt/media_rw/AAAA-AAAA")
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        assertEquals("/mnt/media_rw/AAAA-AAAA/song.mp3", engine.preparedPath)
        assertTrue(engine.playing)
        assertEquals(PlayStatus.PLAYING, coordinator.state.value.status)
        assertEquals(PlayerMode.AUDIO, coordinator.state.value.mode)
    }

    @Test
    fun pauseResumeAndSeek() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        coordinator.pause()
        assertFalse(engine.playing)
        assertEquals(PlayStatus.PAUSED, coordinator.state.value.status)
        coordinator.resume()
        assertTrue(engine.playing)
        coordinator.seekTo(4_000L)
        assertEquals(listOf(4_000L), engine.seeks)
        assertEquals(4_000L, coordinator.state.value.positionMs)
    }

    @Test
    fun nextAndPreviousWrapTheQueue() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 0)
        coordinator.next()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
        coordinator.next()
        coordinator.next()
        assertTrue(engine.preparedPath!!.endsWith("a.mp3"))
        coordinator.previous()
        assertTrue(engine.preparedPath!!.endsWith("c.mp3"))
    }

    @Test
    fun offlineVolumeDoesNotPrepareEngine() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine, snapshots = emptyList())
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        assertNull(engine.preparedPath)
        assertEquals(PlayStatus.ERROR, coordinator.state.value.status)
        assertEquals("USB offline", coordinator.state.value.errorMessage)
    }

    @Test
    fun missingFileSetsErrorWithoutCrash() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine, readable = false)
        coordinator.playQueue(listOf(track("gone.mp3")), 0)
        assertNull(engine.preparedPath)
        assertEquals("File missing", coordinator.state.value.errorMessage)
    }

    @Test
    fun unsupportedMediaSetsError() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("notes.txt", mediaType = "AUDIO")), 0)
        assertEquals(PlayStatus.ERROR, coordinator.state.value.status)
        assertEquals("Unsupported media", coordinator.state.value.errorMessage)
    }

    @Test
    fun usbRemovalDuringPlaybackStopsSafely() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        assertTrue(engine.playing)
        coordinator.onOnlineVolumesChanged(emptySet())
        assertTrue(engine.stopped)
        assertFalse(engine.playing)
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
        assertEquals("USB disconnected", coordinator.state.value.errorMessage)
        assertEquals(null, engine.preparedPath)
    }

    @Test
    fun abandonUsbPlaybackStopsAndClearsPreparedPath() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        assertEquals("/mnt/media_rw/AAAA-AAAA/song.mp3", engine.preparedPath)
        coordinator.abandonUsbPlayback("BROADCAST_EJECT")
        assertTrue(engine.stopped)
        assertFalse(engine.playing)
        assertEquals(null, engine.preparedPath)
        assertEquals("USB disconnected", coordinator.state.value.errorMessage)
    }

    @Test
    fun otherVolumeGoingOfflineDoesNotStopCurrent() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        coordinator.onOnlineVolumesChanged(setOf("AAAA-AAAA"))
        assertTrue(engine.playing)
        assertEquals(PlayStatus.PLAYING, coordinator.state.value.status)
    }

    @Test
    fun sessionSkipStopsAtFirstAndLastQueueItems() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 0)
        assertFalse(coordinator.hasPreviousItem())
        assertTrue(coordinator.hasNextItem())
        coordinator.skipToPrevious()
        assertTrue(engine.preparedPath!!.endsWith("a.mp3"))
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("c.mp3"))
        assertFalse(coordinator.hasNextItem())
        assertTrue(coordinator.hasPreviousItem())
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("c.mp3"))
        coordinator.skipToPrevious()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
        coordinator.skipToPrevious()
        assertTrue(engine.preparedPath!!.endsWith("a.mp3"))
        assertFalse(coordinator.hasPreviousItem())
        coordinator.skipToPrevious()
        assertTrue(engine.preparedPath!!.endsWith("a.mp3"))
    }

    @Test
    fun sessionSkipDoesNotMoveOnASingleItemQueue() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("only.mp3")), 0)
        assertFalse(coordinator.hasNextItem())
        assertFalse(coordinator.hasPreviousItem())
        coordinator.skipToNext()
        coordinator.skipToPrevious()
        assertTrue(engine.preparedPath!!.endsWith("only.mp3"))
    }

    @Test
    fun usbDisconnectStopsServiceHold() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
        coordinator.abandonUsbPlayback("BROADCAST_EJECT")
        assertFalse(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
    }

    @Test
    fun markStartingClearsStaleUsbErrorBeforeResolve() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        coordinator.onOnlineVolumesChanged(emptySet())
        assertEquals("USB disconnected", coordinator.state.value.errorMessage)
        coordinator.markStarting(track("clip.mp4", mediaType = "VIDEO"))
        assertEquals(null, coordinator.state.value.errorMessage)
        assertEquals("clip.mp4", coordinator.state.value.current?.relativePath)
        assertEquals(PlayerMode.VIDEO, coordinator.state.value.mode)
        assertEquals(PlayStatus.BUFFERING, coordinator.state.value.status)
        assertFalse(VideoPlaybackGuard.shouldExitForUsbLoss("clip.mp4", coordinator.state.value))
    }

    @Test
    fun audioFocusLossPausesWithoutDroppingServiceHold() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        coordinator.onEnginePausedBySystem()
        assertEquals(PlayStatus.PAUSED, coordinator.state.value.status)
        assertEquals("song.mp3", coordinator.state.value.current?.relativePath)
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
        assertFalse(engine.stopped)
        coordinator.onEngineResumedBySystem()
        assertEquals(PlayStatus.PLAYING, coordinator.state.value.status)
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
    }

    @Test
    fun systemPauseDoesNotOverrideUsbDisconnect() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        coordinator.abandonUsbPlayback("BROADCAST_EJECT")
        coordinator.onEnginePausedBySystem()
        coordinator.onEngineResumedBySystem()
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
        assertEquals("USB disconnected", coordinator.state.value.errorMessage)
        assertFalse(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
    }

    @Test
    fun usbReconnectRestoresPausedIdentityWithoutAutoplay() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        coordinator.onOnlineVolumesChanged(emptySet())
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
        assertTrue(engine.stopped)
        assertFalse(engine.playing)
        coordinator.onOnlineVolumesChanged(setOf("AAAA-AAAA"))
        assertEquals(PlayStatus.PAUSED, coordinator.state.value.status)
        assertEquals(null, coordinator.state.value.errorMessage)
        assertEquals("song.mp3", coordinator.state.value.current?.relativePath)
        assertTrue(coordinator.state.value.needsPrepare)
        assertFalse(engine.playing)
        assertEquals(null, engine.preparedPath)
        assertFalse(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
    }

    @Test
    fun resumeAfterUsbReconnectPreparesAgain() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("song.mp3")), 0)
        coordinator.abandonUsbPlayback("BROADCAST_EJECT")
        coordinator.onOnlineVolumesChanged(setOf("AAAA-AAAA"))
        assertEquals(PlayStatus.PAUSED, coordinator.state.value.status)
        coordinator.resume()
        assertTrue(engine.playing)
        assertEquals("/mnt/media_rw/AAAA-AAAA/song.mp3", engine.preparedPath)
        assertEquals(PlayStatus.PLAYING, coordinator.state.value.status)
        assertFalse(coordinator.state.value.needsPrepare)
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
    }

    @Test
    fun sessionTransportPlayPauseSeekAndSkip() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.pause()
        assertFalse(engine.playing)
        coordinator.resume()
        assertTrue(engine.playing)
        coordinator.seekTo(2_500L)
        assertEquals(listOf(2_500L), engine.seeks)
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
        coordinator.skipToPrevious()
        assertTrue(engine.preparedPath!!.endsWith("a.mp3"))
    }

    @Test
    fun inAppNextConsumesExplicitQueueThenResumesLibrary() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.playNext(track("n.mp3"))
        coordinator.next()
        assertTrue(engine.preparedPath!!.endsWith("n.mp3"))
        assertTrue(coordinator.explicitQueue.value.isEmpty())
        coordinator.next()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
    }

    @Test
    fun playNextInsertsAheadOfAddToQueue() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.addToQueue(track("queued.mp3"))
        coordinator.playNext(track("next.mp3"))
        assertEquals(listOf("next.mp3", "queued.mp3"), coordinator.explicitQueue.value.map { it.relativePath })
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("next.mp3"))
        assertEquals(listOf("queued.mp3"), coordinator.explicitQueue.value.map { it.relativePath })
    }

    @Test
    fun skipToNextConsumesExplicitQueueThenFallsBackToLibrary() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 0)
        coordinator.addToQueue(track("q.mp3"))
        assertTrue(coordinator.hasNextItem())
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("q.mp3"))
        assertTrue(coordinator.explicitQueue.value.isEmpty())
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
    }

    @Test
    fun explicitQueueGivesMediaSessionNextAtLastLibraryItem() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 1)
        assertFalse(coordinator.hasNextItem())
        coordinator.addToQueue(track("bonus.mp3"))
        assertTrue(coordinator.hasNextItem())
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("bonus.mp3"))
        assertFalse(coordinator.hasNextItem())
        coordinator.skipToNext()
        assertTrue(engine.preparedPath!!.endsWith("bonus.mp3"))
    }

    @Test
    fun removeAndClearExplicitQueue() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3")), 0)
        coordinator.addToQueue(track("q1.mp3"))
        coordinator.addToQueue(track("q2.mp3"))
        coordinator.removeQueued(0)
        assertEquals(listOf("q2.mp3"), coordinator.explicitQueue.value.map { it.relativePath })
        coordinator.clearExplicitQueue()
        assertTrue(coordinator.explicitQueue.value.isEmpty())
        assertFalse(coordinator.hasNextItem())
    }

    @Test
    fun audioEndedStartsNextLibraryTrack() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 0)
        coordinator.onEngineEnded()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
        assertTrue(engine.playing)
        assertEquals(PlayStatus.PLAYING, coordinator.state.value.status)
        assertEquals("b.mp3", coordinator.state.value.current?.relativePath)
    }

    @Test
    fun audioEndedConsumesExplicitQueueFirst() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.addToQueue(track("q.mp3"))
        coordinator.onEngineEnded()
        assertTrue(engine.preparedPath!!.endsWith("q.mp3"))
        assertTrue(coordinator.explicitQueue.value.isEmpty())
        assertTrue(engine.playing)
    }

    @Test
    fun audioEndedFallsBackToLibraryWhenQueueEmpty() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 0)
        coordinator.addToQueue(track("q.mp3"))
        coordinator.onEngineEnded()
        assertTrue(engine.preparedPath!!.endsWith("q.mp3"))
        coordinator.onEngineEnded()
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
        assertTrue(engine.playing)
    }

    @Test
    fun lastAudioTrackWrapsToFirstOnEnded() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 2)
        coordinator.onEngineEnded()
        assertTrue(engine.preparedPath!!.endsWith("a.mp3"))
        assertTrue(engine.playing)
        assertEquals(PlayStatus.PLAYING, coordinator.state.value.status)
    }

    @Test
    fun videoEndedDoesNotAutoAdvanceOrLoop() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(
            listOf(
                track("one.mp4", mediaType = "VIDEO"),
                track("two.mp4", mediaType = "VIDEO")
            ),
            0
        )
        coordinator.onEngineEnded()
        assertTrue(engine.preparedPath!!.endsWith("one.mp4"))
        assertEquals(PlayStatus.ENDED, coordinator.state.value.status)
        assertEquals("one.mp4", coordinator.state.value.current?.relativePath)
        assertFalse(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
    }

    @Test
    fun audioEndedKeepsBackgroundServiceHold() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
        coordinator.onEngineEnded()
        assertTrue(engine.playing)
        assertEquals("b.mp3", coordinator.state.value.current?.relativePath)
        assertTrue(BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value))
    }

    @Test
    fun duplicateEndedBeforePrepareDoesNotSkipTrack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine, dispatcher = dispatcher)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 0)
        advanceUntilIdle()
        assertEquals("a.mp3", coordinator.state.value.current?.relativePath)
        coordinator.onEngineEnded()
        coordinator.onEngineEnded()
        advanceUntilIdle()
        assertEquals("b.mp3", coordinator.state.value.current?.relativePath)
        assertTrue(engine.preparedPath!!.endsWith("b.mp3"))
        assertEquals(PlayStatus.PLAYING, coordinator.state.value.status)
    }

    @Test
    fun lateEndedAfterUsbStopDoesNotAutoAdvance() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.abandonUsbPlayback("BROADCAST_EJECT")
        coordinator.onEngineEnded()
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
        assertEquals("USB disconnected", coordinator.state.value.errorMessage)
        assertEquals("a.mp3", coordinator.state.value.current?.relativePath)
        assertNull(engine.preparedPath)
        assertFalse(engine.playing)
    }

    @Test
    fun lateEndedAfterErrorDoesNotAutoAdvance() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.onEngineError("decode failed")
        coordinator.onEngineEnded()
        assertEquals(PlayStatus.ERROR, coordinator.state.value.status)
        assertEquals("decode failed", coordinator.state.value.errorMessage)
        assertEquals("a.mp3", coordinator.state.value.current?.relativePath)
        assertNull(engine.preparedPath)
    }

    @Test
    fun lateEndedAfterExplicitStopDoesNotAutoAdvance() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.stop()
        coordinator.onEngineEnded()
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
        assertEquals("a.mp3", coordinator.state.value.current?.relativePath)
        assertNull(engine.preparedPath)
    }

    @Test
    fun newPlaybackGenerationCanAutoAdvanceAgain() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3"), track("c.mp3")), 0)
        coordinator.onEngineEnded()
        assertEquals("b.mp3", coordinator.state.value.current?.relativePath)
        coordinator.onEngineEnded()
        assertEquals("c.mp3", coordinator.state.value.current?.relativePath)
        assertTrue(engine.playing)
    }

    @Test
    fun stopForDeletionDoesNotAutoAdvanceOnEnded() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.stopForDeletion("AAAA-AAAA", "a.mp3")
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
        assertEquals("a.mp3", coordinator.state.value.current?.relativePath)
        coordinator.onEngineEnded()
        assertEquals("a.mp3", coordinator.state.value.current?.relativePath)
        assertFalse(engine.playing)
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
    }

    @Test
    fun stopForDeletionIgnoresSynchronousEndedFromEngine() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        engine.onStop = { coordinator.onEngineEnded() }
        coordinator.stopForDeletion("AAAA-AAAA", "a.mp3")
        assertEquals("a.mp3", coordinator.state.value.current?.relativePath)
        assertEquals(PlayStatus.STOPPED, coordinator.state.value.status)
        assertFalse(engine.preparedPath?.endsWith("b.mp3") == true)
    }

    @Test
    fun reconcileDeletedRemovesExplicitQueueAndClearsCurrent() = runTest {
        val engine = FakePlaybackEngine()
        val coordinator = coordinator(engine)
        coordinator.playQueue(listOf(track("a.mp3"), track("b.mp3")), 0)
        coordinator.addToQueue(track("c.mp3"))
        coordinator.stopForDeletion("AAAA-AAAA", "a.mp3")
        coordinator.reconcileDeleted(listOf(MediaIdentity("AAAA-AAAA", "a.mp3"), MediaIdentity("AAAA-AAAA", "c.mp3")))
        assertTrue(coordinator.explicitQueue.value.isEmpty())
        assertNull(coordinator.state.value.current)
        assertEquals(PlayStatus.IDLE, coordinator.state.value.status)
        coordinator.onEngineEnded()
        assertNull(coordinator.state.value.current)
    }

    private fun coordinator(
        engine: FakePlaybackEngine,
        onlineRoot: String = "/mnt/media_rw/AAAA-AAAA",
        snapshots: List<VolumeSnapshot>? = null,
        readable: Boolean = true,
        dispatcher: CoroutineDispatcher? = null
    ): PlaybackCoordinator {
        val io = dispatcher ?: UnconfinedTestDispatcher()
        val resolver = MediaItemResolver(
            snapshotVolumes = {
                snapshots ?: listOf(
                    VolumeSnapshot(
                        description = "USB DISK",
                        state = "mounted",
                        removable = true,
                        isPrimary = false,
                        uuid = "AAAA-AAAA",
                        rootPath = onlineRoot,
                        exists = true,
                        isDirectory = true,
                        canRead = true,
                        listFilesNonNull = true
                    )
                )
            },
            fileReadable = { readable }
        )
        return PlaybackCoordinator(
            resolver = resolver,
            engine = engine,
            scope = CoroutineScope(io),
            ioDispatcher = io,
            mainDispatcher = io
        )
    }

    private fun track(fileName: String, mediaType: String = "AUDIO"): PlayableRef {
        return PlayableRef(
            id = 1L,
            volumeId = "AAAA-AAAA",
            relativePath = fileName,
            fileName = fileName,
            mediaType = mediaType,
            title = fileName,
            artist = "Artist"
        )
    }
}
