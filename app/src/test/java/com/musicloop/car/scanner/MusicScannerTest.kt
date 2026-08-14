package com.musicloop.car.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun uppercaseMp3IsDiscovered() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/SONG.MP3")
        scanner(repo, FakeAudioProbe(mutableListOf(file))).scan(volume, volumeRoot, folder)
        val track = repo.tracksForVolume(volume).single()
        assertEquals("SONG.MP3", track.filename)
        assertEquals("mp3", track.extension)
        assertEquals(ScanState.READY, track.scanState)
    }

    @Test
    fun nestedFoldersAreIndexed() {
        val repo = InMemoryTrackRepository()
        val files = mutableListOf(
            discovered("Music/song1.mp3"),
            discovered("Music/Vietnamese/song3.mp3"),
            discovered("Music/Albums/Album1/song4.mp3")
        )
        scanner(repo, FakeAudioProbe(files)).scan(volume, volumeRoot, folder)
        val paths = repo.tracksForVolume(volume).map { it.relativePath }.toSet()
        assertEquals(
            setOf(
                "Music/song1.mp3",
                "Music/Vietnamese/song3.mp3",
                "Music/Albums/Album1/song4.mp3"
            ),
            paths
        )
    }

    @Test
    fun metadataFailureStillCreatesVisibleTrack() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/song.mp3")
        val metadata = FakeMetadataReader {
            MetadataResult(
                success = false,
                errorClass = "java.lang.RuntimeException",
                errorMessage = "setDataSource failed"
            )
        }
        scanner(repo, FakeAudioProbe(mutableListOf(file)), metadata).scan(volume, volumeRoot, folder)
        val tracks = repo.tracksForVolume(volume)
        assertEquals(1, tracks.size)
        val track = tracks[0]
        assertEquals("song", track.title)
        assertEquals("", track.artist)
        assertEquals("", track.album)
        assertNull(track.durationMs)
        assertEquals(ScanState.READY, track.scanState)
        assertEquals(MetadataState.UNVERIFIED, track.metadataState)
        assertFalse(track.isUnplayable)
        assertEquals(PlayableState.UNKNOWN, track.playableState)
    }

    @Test
    fun metadataFailureDoesNotMarkPermanentUnplayable() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/broken.mp3")
        val metadata = FakeMetadataReader { MetadataResult(success = false) }
        val probe = FakeAudioProbe(mutableListOf(file))
        repeat(3) {
            scanner(repo, probe, metadata, maxFailures = 3).scan(volume, volumeRoot, folder)
        }
        val track = repo.tracksForVolume(volume).single()
        assertEquals(ScanState.READY, track.scanState)
        assertEquals(MetadataState.UNVERIFIED, track.metadataState)
        assertFalse(track.isUnplayable)
        assertEquals(PlayableState.UNKNOWN, track.playableState)
    }

    @Test
    fun emptyMetadataFallsBackToFilename() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/01 - Song Name.mp3")
        val metadata = FakeMetadataReader {
            MetadataResult(success = true, title = "", artist = "", album = "", durationMs = null)
        }
        scanner(repo, FakeAudioProbe(mutableListOf(file)), metadata).scan(volume, volumeRoot, folder)
        val track = repo.tracksForVolume(volume).single()
        assertEquals("01 - Song Name", track.title)
        assertEquals("", track.artist)
        assertEquals("", track.album)
        assertNull(track.durationMs)
        assertEquals(ScanState.READY, track.scanState)
        assertEquals(MetadataState.READY, track.metadataState)
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
    fun wrongVolumeIdentityReturnsEmptyLibrary() {
        val repo = InMemoryTrackRepository()
        scanner(repo, FakeAudioProbe(mutableListOf(discovered("Music/song.mp3"))))
            .scan(volume, volumeRoot, folder)
        assertEquals(1, repo.tracksForVolume(volume).size)
        assertTrue(repo.tracksForVolume("WRONG-ID").isEmpty())
    }

    @Test
    fun roomLibraryQueryReturnsIndexedTracks() {
        val repo = InMemoryTrackRepository()
        scanner(
            repo,
            FakeAudioProbe(
                mutableListOf(
                    discovered("Music/a.mp3"),
                    discovered("Music/b.mp3")
                )
            )
        ).scan(volume, volumeRoot, folder)
        val library = repo.tracksForVolume(volume)
        assertEquals(2, library.size)
        assertEquals(setOf("a.mp3", "b.mp3"), library.map { it.filename }.toSet())
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
    fun stableFileIsIndexedReady() {
        val repo = InMemoryTrackRepository()
        val file = discovered("Music/stable.mp3", size = 2048, mtime = 50L)
        scanner(repo, FakeAudioProbe(mutableListOf(file))).scan(volume, volumeRoot, folder)
        val track = repo.tracksForVolume(volume).single()
        assertEquals(ScanState.READY, track.scanState)
        assertEquals(2048L, track.fileSize)
        assertEquals(50L, track.lastModified)
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

    @Test
    fun diagnosticsCaptureLibraryCountAfterMetadataFailure() {
        val repo = InMemoryTrackRepository()
        val scanner = scanner(
            repo,
            FakeAudioProbe(mutableListOf(discovered("Music/song.mp3"))),
            FakeMetadataReader { MetadataResult(success = false) }
        )
        scanner.scan(volume, volumeRoot, folder)
        assertEquals(1, scanner.lastDiagnostics.libraryCount)
        assertEquals(1, scanner.lastDiagnostics.acceptedAudioFiles)
        assertEquals(0, scanner.lastDiagnostics.metadataOk)
        assertEquals(1, scanner.lastDiagnostics.metadataFailed)
        assertTrue(scanner.lastDiagnostics.formatSummary().contains("Library:"))
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
