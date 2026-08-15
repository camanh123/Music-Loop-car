package com.musicloop.car.playback

import com.musicloop.car.storage.VolumeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private fun coordinator(
        engine: FakePlaybackEngine,
        onlineRoot: String = "/mnt/media_rw/AAAA-AAAA",
        snapshots: List<VolumeSnapshot>? = null,
        readable: Boolean = true
    ): PlaybackCoordinator {
        val dispatcher = UnconfinedTestDispatcher()
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
            scope = CoroutineScope(dispatcher),
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
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
