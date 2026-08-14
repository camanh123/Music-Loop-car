package com.musicloop.car.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {

    private val volume = "AAAA-AAAA"
    private val root = "/storage/USB2"

    private fun item(
        relative: String,
        playable: Boolean = true,
        durationMs: Long = 180_000L,
        id: Long = relative.hashCode().toLong()
    ): QueueItem {
        val filename = relative.substringAfterLast('/')
        return QueueItem(
            id = id,
            volumeIdentity = volume,
            relativePath = relative,
            title = filename,
            artist = "Artist",
            album = "Album",
            durationMs = durationMs,
            filename = filename,
            extension = filename.substringAfterLast('.', "mp3"),
            playable = playable
        )
    }

    private fun controller(
        engine: FakePlayerEngine = FakePlayerEngine(),
        store: InMemoryPlaybackStore = InMemoryPlaybackStore(),
        readable: MutableSet<String> = mutableSetOf("a.mp3", "b.mp3", "c.mp3"),
        clock: () -> Long = { 1_000L }
    ): PlaybackController {
        val controller = PlaybackController(
            engine = engine,
            store = store,
            audioFocus = NoOpAudioFocusGate,
            fileAccess = MapFileAccess(readable),
            clock = clock,
            listener = { }
        )
        controller.setVolumeContext(volume, root, true)
        return controller
    }

    @Test
    fun playSelectedTrackGoesPlaying() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        val track = item("a.mp3")
        playback.setQueue(listOf(track, item("b.mp3")))
        playback.playUserSelected(track)
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
        assertEquals("$root/a.mp3", engine.lastPath)
        assertTrue(engine.playing)
    }

    @Test
    fun pauseThenPlayResumesWithoutNewPrepareWhenPaused() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        val track = item("a.mp3")
        playback.setQueue(listOf(track))
        playback.playUserSelected(track)
        engine.positionMs = 12_000
        playback.pause()
        assertEquals(PlayerState.PAUSED, playback.snapshot().state)
        val prepares = engine.lastPath
        playback.playPause()
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
        assertEquals(prepares, engine.lastPath)
        assertEquals(2, engine.startCount)
    }

    @Test
    fun idlePlayPreparesAndStarts() {
        val engine = FakePlayerEngine()
        val store = InMemoryPlaybackStore()
        store.saved = SavedPlaybackState(volume, "a.mp3", 84_000, 1L, durationMs = 180_000)
        val playback = controller(engine, store)
        playback.setQueue(listOf(item("a.mp3")))
        assertEquals(PlayerState.IDLE, playback.snapshot().state)
        assertEquals(84_000, playback.snapshot().positionMs)
        playback.playPause()
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
        assertEquals(84_000, engine.positionMs)
    }

    @Test
    fun seekClampsToDuration() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3")))
        playback.playUserSelected(item("a.mp3"))
        playback.seekTo(500_000)
        assertEquals(0, playback.snapshot().positionMs)
        playback.seekTo(12_345)
        assertEquals(12_345, engine.positionMs)
    }

    @Test
    fun nextSkipsUnplayableAndMissingTracks() {
        val engine = FakePlayerEngine()
        val playback = controller(
            engine = engine,
            readable = mutableSetOf("a.mp3", "d.mp3")
        )
        val tracks = listOf(
            item("a.mp3"),
            item("b.mp3", playable = false),
            item("c.mp3"),
            item("d.mp3")
        )
        playback.setQueue(tracks)
        playback.playUserSelected(tracks[0])
        playback.next()
        assertEquals("d.mp3", playback.snapshot().track?.filename)
        assertEquals("$root/d.mp3", engine.lastPath)
    }

    @Test
    fun previousRestartsCurrentTrackAfterFiveSeconds() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3"), item("b.mp3")))
        playback.playUserSelected(item("b.mp3"))
        engine.positionMs = 8000
        playback.previous()
        assertEquals("b.mp3", playback.snapshot().track?.filename)
        assertEquals(0, playback.snapshot().positionMs)
    }

    @Test
    fun previousSelectsPreviousPlayableWhenNearStart() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3"), item("b.mp3")))
        playback.playUserSelected(item("b.mp3"))
        engine.positionMs = 1000
        playback.previous()
        assertEquals("a.mp3", playback.snapshot().track?.filename)
    }

    @Test
    fun emptyLibraryShowsNoPlayableMessage() {
        val playback = controller(readable = mutableSetOf())
        playback.setQueue(emptyList())
        playback.next()
        assertEquals(PlaybackMessage.NO_PLAYABLE_TRACK, playback.snapshot().message)
    }

    @Test
    fun oneTrackLibraryNextWrapsToSameTrack() {
        val playback = controller()
        playback.setQueue(listOf(item("a.mp3")))
        playback.playUserSelected(item("a.mp3"))
        playback.next()
        assertEquals("a.mp3", playback.snapshot().track?.filename)
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
    }

    @Test
    fun completionAtEndOfLibraryStopsWithoutWrapping() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3"), item("b.mp3")))
        playback.playUserSelected(item("b.mp3"))
        engine.complete()
        assertEquals(PlayerState.COMPLETED, playback.snapshot().state)
        assertEquals("b.mp3", playback.snapshot().track?.filename)
    }

    @Test
    fun completionAdvancesToNextPlayableTrack() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3"), item("b.mp3")))
        playback.playUserSelected(item("a.mp3"))
        engine.complete()
        assertEquals("b.mp3", playback.snapshot().track?.filename)
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
    }

    @Test
    fun repeatOneReplaysTheSameTrack() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3"), item("b.mp3")))
        playback.playUserSelected(item("a.mp3"))
        playback.cycleRepeatMode()
        assertEquals(RepeatMode.ONE, playback.snapshot().repeatMode)
        engine.complete()
        assertEquals("a.mp3", playback.snapshot().track?.filename)
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
    }

    @Test
    fun usbRemovalStopsAndReleasesWithoutAutoplayOnReconnect() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3")))
        playback.playUserSelected(item("a.mp3"))
        engine.positionMs = 42_000
        playback.onUsbDisconnected()
        assertEquals(PlayerState.USB_DISCONNECTED, playback.snapshot().state)
        assertEquals(PlaybackMessage.USB_DISCONNECTED, playback.snapshot().message)
        assertTrue(engine.released)
        assertFalse(engine.playing)
        playback.setVolumeContext(volume, root, true)
        assertEquals(PlayerState.IDLE, playback.snapshot().state)
        assertFalse(engine.playing)
        assertEquals("a.mp3", playback.snapshot().track?.filename)
    }

    @Test
    fun restoreDoesNotAutoPlay() {
        val store = InMemoryPlaybackStore()
        store.saved = SavedPlaybackState(
            volumeIdentity = volume,
            relativePath = "a.mp3",
            positionMs = 84_000,
            updatedAt = 10L,
            title = "a.mp3",
            durationMs = 180_000
        )
        val engine = FakePlayerEngine()
        val playback = controller(engine, store)
        playback.setQueue(listOf(item("a.mp3")))
        val snapshot = playback.snapshot()
        assertEquals(PlayerState.IDLE, snapshot.state)
        assertEquals(84_000, snapshot.positionMs)
        assertEquals(0, engine.startCount)
    }

    @Test
    fun restoreClampsInvalidPosition() {
        val store = InMemoryPlaybackStore()
        store.saved = SavedPlaybackState(volume, "a.mp3", 999_999, 1L, durationMs = 180_000)
        val playback = controller(store = store)
        playback.setQueue(listOf(item("a.mp3")))
        assertEquals(0, playback.snapshot().positionMs)
    }

    @Test
    fun playbackErrorDoesNotCrashAndAllowsAnotherTrack() {
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        playback.setQueue(listOf(item("a.mp3"), item("b.mp3")))
        playback.playUserSelected(item("a.mp3"))
        engine.error(
            PlaybackError(
                kind = PlaybackErrorKind.UNSUPPORTED,
                what = 1,
                extra = -1010
            )
        )
        assertEquals(PlayerState.ERROR, playback.snapshot().state)
        assertEquals(PlaybackMessage.CANNOT_PLAY_FILE, playback.snapshot().message)
        playback.playUserSelected(item("b.mp3"))
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
        assertEquals("b.mp3", playback.snapshot().track?.filename)
    }

    @Test
    fun tappingUnplayableStillAttemptsPlaybackIfFileExists() {
        val engine = FakePlayerEngine()
        val playback = controller(engine, readable = mutableSetOf("bad.mp3"))
        val unplayable = item("bad.mp3", playable = false)
        playback.setQueue(listOf(unplayable))
        playback.playUserSelected(unplayable)
        assertEquals(PlayerState.PLAYING, playback.snapshot().state)
    }

    @Test
    fun prepareFailureGoesToErrorWithoutDeletingTrack() {
        val engine = FakePlayerEngine().apply { failPrepare = true }
        val playback = controller(engine)
        val track = item("a.mp3")
        playback.setQueue(listOf(track, item("b.mp3")))
        playback.playUserSelected(track)
        assertEquals(PlayerState.ERROR, playback.snapshot().state)
        assertEquals("a.mp3", playback.snapshot().track?.filename)
        assertEquals(PlaybackMessage.CANNOT_PLAY_FILE, playback.snapshot().message)
    }

    @Test
    fun persistUsesIntervalAndForcedEvents() {
        var now = 0L
        val store = InMemoryPlaybackStore()
        val engine = FakePlayerEngine()
        val playback = controller(engine, store, clock = { now })
        playback.setQueue(listOf(item("a.mp3")))
        playback.playUserSelected(item("a.mp3"))
        val afterPlay = store.saved!!.updatedAt
        now = 1_000L
        engine.positionMs = 1_000
        playback.onProgressTick()
        assertEquals(afterPlay, store.saved!!.updatedAt)
        now = 6_000L
        engine.positionMs = 6_000
        playback.onProgressTick()
        assertEquals(6_000L, store.saved!!.updatedAt)
        assertEquals(6_000, store.saved!!.positionMs)
    }

    @Test
    fun allUnplayableNextShowsNoPlayableMessage() {
        val playback = controller(readable = mutableSetOf())
        playback.setQueue(listOf(item("a.mp3", playable = false), item("b.mp3", playable = false)))
        playback.playUserSelected(item("a.mp3", playable = false))
        playback.next()
        assertEquals(PlaybackMessage.NO_PLAYABLE_TRACK, playback.snapshot().message)
    }

    @Test
    fun neverUsesStaleUsb1PathWhenVolumeIsUsb2() {
        val engine = FakePlayerEngine()
        val playback = controller(
            engine = engine,
            readable = mutableSetOf("Music/song.mp3")
        )
        playback.setQueue(listOf(item("Music/song.mp3")))
        playback.playUserSelected(item("Music/song.mp3"))
        assertEquals("$root/Music/song.mp3", engine.lastPath)
        assertNotEquals("/storage/USB1/Music/song.mp3", engine.lastPath)
    }
}
