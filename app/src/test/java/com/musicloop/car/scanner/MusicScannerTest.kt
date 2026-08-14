package com.musicloop.car.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicScannerTest {

    private val volume = "AAAA-AAAA"
    private val volumeRoot = "/storage/USB1"
    private val folder = "/storage/USB1/Music"

    @Test
    fun newMp3IsDiscoveredAndIndexed() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/song.mp3")
        val scanner = scanner(repo, FakeAudioProbe(mutableListOf(file)))
        val outcome = scanner.scan(volume, volumeRoot, folder)
        assertEquals(ScanPhase.COMPLETE, outcome.phase)
        val tracks = repo.tracksForVolume(volume)
        assertEquals(1, tracks.size)
        assertEquals("Music/song.mp3", tracks[0].relativePath)
        assertEquals(ScanState.READY, tracks[0].scanState)
        assertEquals(1, scanner.metadataReadCount)
    }

    @Test
    fun unchangedFileIsNotRescanned() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/song.mp3")
        val probe = FakeAudioProbe(mutableListOf(file))
        val metadata = FakeMetadataReader()
        val scanner = scanner(repo, probe, metadata)
        scanner.scan(volume, volumeRoot, folder)
        assertEquals(1, metadata.reads.size)
        scanner.scan(volume, volumeRoot, folder)
        assertEquals(1, metadata.reads.size)
    }

    @Test
    fun changedFileIsRescanned() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/song.mp3", size = 1000)
        val probe = FakeAudioProbe(mutableListOf(file))
        val metadata = FakeMetadataReader()
        val scanner = scanner(repo, probe, metadata)
        scanner.scan(volume, volumeRoot, folder)
        probe.files[0] = file.copy(size = 2000, lastModified = 99L)
        scanner.scan(volume, volumeRoot, folder)
        assertEquals(2, metadata.reads.size)
    }

    @Test
    fun missingFileIsNotDeletedUntilCompleteScan() {
        val repo = InMemoryTrackRepository()
        val keep = discovered("Music/keep.mp3")
        val gone = discovered("Music/gone.mp3")
        val probe = FakeAudioProbe(mutableListOf(keep, gone))
        scanner(repo, probe).scan(volume, volumeRoot, folder)
        assertEquals(2, repo.tracksForVolume(volume).size)

        probe.throwOnList = true
        val interrupted = scanner(repo, probe).scan(volume, volumeRoot, folder)
        assertEquals(ScanPhase.INTERRUPTED, interrupted.phase)
        assertEquals(2, repo.tracksForVolume(volume).size)
    }

    @Test
    fun completeScanRemovesMissingFile() {
        val repo = InMemoryTrackRepository()
        val keep = discovered("Music/keep.mp3")
        val gone = discovered("Music/gone.mp3")
        val probe = FakeAudioProbe(mutableListOf(keep, gone))
        scanner(repo, probe).scan(volume, volumeRoot, folder)
        probe.files = mutableListOf(keep)
        scanner(repo, probe).scan(volume, volumeRoot, folder)
        val remaining = repo.tracksForVolume(volume).map { it.filename }
        assertEquals(listOf("keep.mp3"), remaining)
    }

    @Test
    fun usbRemovedDuringScanPreservesRecords() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/song.mp3")
        val probe = FakeAudioProbe(mutableListOf(file))
        scanner(repo, probe).scan(volume, volumeRoot, folder)
        probe.volumePresent = false
        val outcome = scanner(repo, probe).scan(volume, volumeRoot, folder)
        assertEquals(ScanPhase.INTERRUPTED, outcome.phase)
        assertEquals(1, repo.tracksForVolume(volume).size)
    }

    @Test
    fun usbReinsertSameIdentityDoesNotDuplicate() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/song.mp3")
        scanner(repo, FakeAudioProbe(mutableListOf(file))).scan(volume, volumeRoot, folder)
        scanner(repo, FakeAudioProbe(mutableListOf(file))).scan(
            volume,
            "/storage/USB2",
            "/storage/USB2/Music"
        )
        assertEquals(1, repo.tracksForVolume(volume).size)
        assertTrue(repo.tracksForVolume(volume)[0].absolutePath.startsWith("/storage/USB2"))
    }

    @Test
    fun differentVolumeIsNotMerged() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/song.mp3")
        scanner(repo, FakeAudioProbe(mutableListOf(file))).scan("AAAA-AAAA", volumeRoot, folder)
        scanner(repo, FakeAudioProbe(mutableListOf(file))).scan(
            "BBBB-BBBB",
            "/storage/USB2",
            "/storage/USB2/Music"
        )
        assertEquals(1, repo.tracksForVolume("AAAA-AAAA").size)
        assertEquals(1, repo.tracksForVolume("BBBB-BBBB").size)
    }

    @Test
    fun missingMetadataFallsBackToFilename() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/01 - Em Cua Ngay Hom Qua.mp3")
        val metadata = FakeMetadataReader { MetadataResult(success = true, title = null, artist = null) }
        scanner(repo, FakeAudioProbe(mutableListOf(file)), metadata).scan(volume, volumeRoot, folder)
        assertEquals("01 - Em Cua Ngay Hom Qua", repo.tracksForVolume(volume)[0].title)
    }

    @Test
    fun temporaryMetadataFailureRetriesThenUnplayable() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/broken.mp3")
        val metadata = FakeMetadataReader { MetadataResult(success = false) }
        val probe = FakeAudioProbe(mutableListOf(file))
        val first = scanner(repo, probe, metadata, maxFailures = 3)
        first.scan(volume, volumeRoot, folder)
        assertEquals(MetadataState.UNVERIFIED, repo.tracksForVolume(volume)[0].metadataState)
        assertFalse(repo.tracksForVolume(volume)[0].isUnplayable)

        scanner(repo, probe, metadata, maxFailures = 3).scan(volume, volumeRoot, folder)
        scanner(repo, probe, metadata, maxFailures = 3).scan(volume, volumeRoot, folder)
        val track = repo.tracksForVolume(volume)[0]
        assertEquals(ScanState.UNPLAYABLE, track.scanState)
        assertTrue(track.isUnplayable)
        assertEquals(3, track.verifyFailures)
    }

    @Test
    fun changingSizeStaysWaitingStableAndIsNotUnplayable() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/recording.mp3", size = 100)
        val probe = FakeAudioProbe(mutableListOf(file))
        val abs = "/storage/USB1/Music/recording.mp3"
        probe.snapshots[abs] = ArrayDeque(
            listOf(
                FileSnapshot(true, 100, 1),
                FileSnapshot(true, 250, 2)
            )
        )
        val metadata = FakeMetadataReader()
        scanner(repo, probe, metadata).scan(volume, volumeRoot, folder)
        val track = repo.tracksForVolume(volume)[0]
        assertEquals(ScanState.WAITING_STABLE, track.scanState)
        assertFalse(track.isUnplayable)
        assertEquals(0, metadata.reads.size)
    }

    @Test
    fun cancelDuringScanDoesNotPrune() {
        val repo = InMemoryTrackRepository()
        val keep = discovered("Music/keep.mp3")
        scanner(repo, FakeAudioProbe(mutableListOf(keep))).scan(volume, volumeRoot, folder)
        var cancelled = false
        val probe = FakeAudioProbe(mutableListOf(keep, discovered("Music/new.mp3")))
        val scanner = MusicScanner(
            probe = probe,
            metadataReader = FakeMetadataReader(),
            repository = repo,
            clock = ScanClock { 1L },
            sleeper = ScanSleeper { },
            isCancelled = { cancelled },
            stabilityWaitMs = 0L
        )
        cancelled = true
        val outcome = scanner.scan(volume, volumeRoot, folder)
        assertEquals(ScanPhase.INTERRUPTED, outcome.phase)
        assertEquals(1, repo.tracksForVolume(volume).size)
    }

    private fun discovered(
        relative: String,
        size: Long = 1000L,
        mtime: Long = 10L
    ): DiscoveredFile {
        val name = relative.substringAfterLast('/')
        return DiscoveredFile(
            relativePath = relative,
            filename = name,
            extension = AudioFileFilter.extensionOf(name) ?: "",
            size = size,
            lastModified = mtime
        )
    }

    private fun scanner(
        repo: InMemoryTrackRepository,
        probe: FakeAudioProbe,
        metadata: FakeMetadataReader = FakeMetadataReader(),
        maxFailures: Int = ScanPolicy.MAX_STABLE_FAILURES
    ): MusicScanner {
        return MusicScanner(
            probe = probe,
            metadataReader = metadata,
            repository = repo,
            clock = ScanClock { 1L },
            sleeper = ScanSleeper { },
            isCancelled = { false },
            stabilityWaitMs = 0L,
            maxStableFailures = maxFailures
        )
    }
}
