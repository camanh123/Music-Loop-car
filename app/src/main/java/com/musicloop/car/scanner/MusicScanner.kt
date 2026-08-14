package com.musicloop.car.scanner

import com.musicloop.car.storage.MusicFolderPaths

/**
 * Incremental, read-only USB music scanner.
 *
 * Never writes to USB. Room/repository writes are the caller's internal storage.
 */
class MusicScanner(
    private val probe: AudioFileProbe,
    private val metadataReader: MetadataReader,
    private val repository: TrackRepository,
    private val clock: ScanClock,
    private val sleeper: ScanSleeper,
    private val isCancelled: () -> Boolean,
    private val onProgress: (ScanProgress) -> Unit = {},
    private val stabilityWaitMs: Long = ScanPolicy.STABILITY_WAIT_MS,
    private val maxStableFailures: Int = ScanPolicy.MAX_STABLE_FAILURES
) {

    var metadataReadCount: Int = 0
        private set

    fun scan(
        volumeIdentity: String,
        volumeRoot: String,
        folderAbsolute: String
    ): ScanOutcome {
        metadataReadCount = 0
        emit(ScanPhase.ENUMERATING)
        if (shouldAbort()) {
            return interrupted(0, 0)
        }

        val enumerated = try {
            probe.listAudioFiles(folderAbsolute, volumeRoot)
        } catch (_: Exception) {
            return interrupted(0, 0)
        }

        if (shouldAbort()) {
            return interrupted(enumerated.size, 0)
        }

        val existing = repository.tracksForVolume(volumeIdentity).associateBy { it.relativePath }
        val seen = mutableSetOf<String>()
        var processed = 0

        for (file in enumerated) {
            if (shouldAbort()) {
                return interrupted(enumerated.size, processed)
            }
            seen += file.relativePath
            processed += 1
            emit(ScanPhase.CHECKING, processed, enumerated.size, volumeIdentity)
            processFile(volumeIdentity, volumeRoot, file, existing[file.relativePath])
        }

        if (shouldAbort()) {
            return interrupted(enumerated.size, processed)
        }

        val folderRelative = MusicFolderPaths.relativeToVolume(volumeRoot, folderAbsolute) ?: ""
        repository.removeConfirmedMissing(volumeIdentity, folderRelative, seen)
        emit(ScanPhase.COMPLETE, processed, enumerated.size, volumeIdentity)
        return ScanOutcome(ScanPhase.COMPLETE, enumerated.size, processed)
    }

    private fun processFile(
        volumeIdentity: String,
        volumeRoot: String,
        file: DiscoveredFile,
        existing: AudioTrack?
    ) {
        val absolute = MusicFolderPaths.join(volumeRoot, file.relativePath)
        val current = existing
        val unchanged = current != null &&
            current.fileSize == file.size &&
            current.lastModified == file.lastModified &&
            current.scanState == ScanState.READY &&
            current.metadataState == MetadataState.READY &&
            !current.missingConfirmed

        if (current != null && unchanged) {
            repository.upsert(
                current.copy(
                    absolutePath = absolute,
                    lastVerifiedAt = clock.now(),
                    updatedAt = clock.now(),
                    missingConfirmed = false
                )
            )
            return
        }

        val discovered = baseTrack(volumeIdentity, absolute, file, existing).copy(
            scanState = ScanState.DISCOVERED,
            metadataState = existing?.metadataState ?: MetadataState.METADATA_PENDING
        )
        repository.upsert(discovered)

        val first = probe.snapshot(absolute)
        if (first == null || !first.exists) {
            return
        }
        if (stabilityWaitMs > 0L) {
            sleeper.sleep(stabilityWaitMs)
        }
        if (shouldAbort()) {
            return
        }
        val second = probe.snapshot(absolute)
        if (second == null || !second.exists) {
            return
        }
        if (first.size != second.size || first.lastModified != second.lastModified) {
            repository.upsert(
                discovered.copy(
                    fileSize = second.size,
                    lastModified = second.lastModified,
                    scanState = ScanState.WAITING_STABLE,
                    metadataState = MetadataState.METADATA_PENDING,
                    updatedAt = clock.now()
                )
            )
            return
        }

        val verifying = discovered.copy(
            fileSize = second.size,
            lastModified = second.lastModified,
            scanState = ScanState.VERIFYING,
            updatedAt = clock.now()
        )
        repository.upsert(verifying)

        metadataReadCount += 1
        val meta = try {
            metadataReader.read(absolute)
        } catch (_: Exception) {
            MetadataResult(success = false)
        }

        if (shouldAbort()) {
            return
        }

        if (!meta.success) {
            val failures = (existing?.verifyFailures ?: 0) + 1
            val unplayable = failures >= maxStableFailures
            repository.upsert(
                verifying.copy(
                    scanState = if (unplayable) ScanState.UNPLAYABLE else ScanState.ERROR,
                    metadataState = if (unplayable) MetadataState.UNPLAYABLE else MetadataState.UNVERIFIED,
                    verifyFailures = failures,
                    lastVerifiedAt = clock.now(),
                    updatedAt = clock.now()
                )
            )
            return
        }

        val title = meta.title?.takeIf { it.isNotBlank() } ?: FilenameTitleParser.titleFromFilename(file.filename)
        repository.upsert(
            verifying.copy(
                title = title,
                artist = meta.artist?.takeIf { it.isNotBlank() } ?: existing?.artist.orEmpty(),
                album = meta.album?.takeIf { it.isNotBlank() } ?: existing?.album.orEmpty(),
                albumArtist = meta.albumArtist?.takeIf { it.isNotBlank() } ?: existing?.albumArtist.orEmpty(),
                genre = meta.genre?.takeIf { it.isNotBlank() } ?: existing?.genre.orEmpty(),
                trackNumber = meta.trackNumber ?: existing?.trackNumber,
                durationMs = meta.durationMs ?: existing?.durationMs,
                scanState = ScanState.READY,
                metadataState = MetadataState.READY,
                playableState = PlayableState.UNKNOWN,
                verifyFailures = 0,
                lastVerifiedAt = clock.now(),
                updatedAt = clock.now(),
                missingConfirmed = false
            )
        )
    }

    private fun baseTrack(
        volumeIdentity: String,
        absolute: String,
        file: DiscoveredFile,
        existing: AudioTrack?
    ): AudioTrack {
        return AudioTrack(
            id = existing?.id ?: 0L,
            volumeIdentity = volumeIdentity,
            relativePath = file.relativePath,
            absolutePath = absolute,
            filename = file.filename,
            extension = file.extension,
            title = existing?.title ?: FilenameTitleParser.titleFromFilename(file.filename),
            artist = existing?.artist.orEmpty(),
            album = existing?.album.orEmpty(),
            albumArtist = existing?.albumArtist.orEmpty(),
            genre = existing?.genre.orEmpty(),
            trackNumber = existing?.trackNumber,
            durationMs = existing?.durationMs,
            fileSize = file.size,
            lastModified = file.lastModified,
            artworkKey = existing?.artworkKey,
            scanState = ScanState.DISCOVERED,
            metadataState = MetadataState.METADATA_PENDING,
            playableState = existing?.playableState ?: PlayableState.UNKNOWN,
            favorite = existing?.favorite ?: false,
            verifyFailures = existing?.verifyFailures ?: 0,
            lastVerifiedAt = existing?.lastVerifiedAt ?: 0L,
            missingConfirmed = false,
            updatedAt = clock.now()
        )
    }

    private fun shouldAbort(): Boolean {
        return isCancelled() || !probe.isVolumePresent()
    }

    private fun interrupted(total: Int, processed: Int): ScanOutcome {
        emit(ScanPhase.INTERRUPTED, processed, total)
        return ScanOutcome(ScanPhase.INTERRUPTED, total, processed)
    }

    private fun emit(
        phase: ScanPhase,
        processed: Int = 0,
        total: Int = 0,
        volumeIdentity: String? = null
    ) {
        val tracks = volumeIdentity?.let { repository.tracksForVolume(it) }.orEmpty()
        onProgress(
            ScanProgress(
                phase = phase,
                processed = processed,
                total = total,
                readyCount = tracks.count { it.scanState == ScanState.READY },
                unverifiedCount = tracks.count {
                    it.metadataState == MetadataState.UNVERIFIED ||
                        it.scanState == ScanState.WAITING_STABLE ||
                        it.metadataState == MetadataState.METADATA_PENDING
                },
                unplayableCount = tracks.count { it.isUnplayable },
                indexedCount = tracks.size
            )
        )
    }
}
