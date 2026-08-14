package com.musicloop.car.scanner

import com.musicloop.car.storage.MusicFolderPaths
import java.io.File

/**
 * Incremental, read-only USB music scanner.
 *
 * Discovery is independent of metadata extraction. A stable, readable, supported
 * audio file is always indexed as a visible library track. Metadata is enrichment.
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
    @Suppress("UNUSED_PARAMETER")
    private val maxStableFailures: Int = ScanPolicy.MAX_STABLE_FAILURES
) {

    var metadataReadCount: Int = 0
        private set

    var lastDiagnostics: ScanDiagnostics = ScanDiagnostics()
        private set

    fun scan(
        volumeIdentity: String,
        volumeRoot: String,
        folderAbsolute: String
    ): ScanOutcome {
        metadataReadCount = 0
        var diagnostics = ScanDiagnostics(
            volumeIdentity = volumeIdentity,
            volumeRoot = volumeRoot,
            folderAbsolute = folderAbsolute
        )
        lastDiagnostics = diagnostics

        ScannerLog.i("SCAN_START")
        ScannerLog.i("volumeIdentity=$volumeIdentity")
        ScannerLog.i("volumeRoot=$volumeRoot")
        ScannerLog.i("folderAbsolute=$folderAbsolute")

        emit(ScanPhase.ENUMERATING, diagnostics = diagnostics)
        if (shouldAbort()) {
            return interrupted(0, 0, diagnostics)
        }

        val enumerated = try {
            probe.listAudioFiles(folderAbsolute, volumeRoot)
        } catch (error: Exception) {
            ScannerLog.error("listAudioFiles", error)
            diagnostics = diagnostics.copy(
                folderExists = fileFlag { File(folderAbsolute).exists() },
                folderIsDirectory = fileFlag { File(folderAbsolute).isDirectory },
                folderCanRead = fileFlag { File(folderAbsolute).canRead() }
            )
            lastDiagnostics = diagnostics
            return interrupted(0, 0, diagnostics)
        }

        diagnostics = diagnostics.copy(
            folderExists = enumerated.folderExists,
            folderIsDirectory = enumerated.folderIsDirectory,
            folderCanRead = enumerated.folderCanRead,
            folderAbsolute = enumerated.folderAbsolutePath.ifBlank { folderAbsolute },
            totalFilesystemEntries = enumerated.totalFilesystemEntries,
            audioCandidates = enumerated.audioCandidates,
            acceptedAudioFiles = enumerated.acceptedAudioFiles,
            rejectedFiles = enumerated.rejectedFiles
        )
        lastDiagnostics = diagnostics

        if (enumerated.rootUnreadable) {
            ScannerLog.w("scan root unreadable folderAbsolute=$folderAbsolute")
            return interrupted(0, 0, diagnostics)
        }

        if (shouldAbort()) {
            return interrupted(enumerated.files.size, 0, diagnostics)
        }

        val existingList = repository.tracksForVolume(volumeIdentity)
        val existing = existingList.associateBy { it.relativePath }
        diagnostics = diagnostics.copy(rowsBefore = existingList.size)
        lastDiagnostics = diagnostics
        ScannerLog.i("ROOM_RESULT rows before=${existingList.size}")

        val seen = mutableSetOf<String>()
        var processed = 0
        var upserts = 0
        var metadataOk = 0
        var metadataFailed = 0

        for (file in enumerated.files) {
            if (shouldAbort()) {
                diagnostics = diagnostics.copy(
                    metadataOk = metadataOk,
                    metadataFailed = metadataFailed,
                    rowsInsertedOrUpdated = upserts,
                    rowsAfter = repository.tracksForVolume(volumeIdentity).size,
                    libraryCount = repository.tracksForVolume(volumeIdentity).size
                )
                lastDiagnostics = diagnostics
                return interrupted(enumerated.files.size, processed, diagnostics)
            }
            seen += file.relativePath
            processed += 1
            emit(
                ScanPhase.CHECKING,
                processed,
                enumerated.files.size,
                volumeIdentity,
                diagnostics
            )
            val result = processFile(volumeIdentity, volumeRoot, file, existing[file.relativePath])
            upserts += result.upserts
            metadataOk += if (result.metadataSuccess == true) 1 else 0
            metadataFailed += if (result.metadataSuccess == false) 1 else 0
        }

        if (shouldAbort()) {
            diagnostics = diagnostics.copy(
                metadataOk = metadataOk,
                metadataFailed = metadataFailed,
                rowsInsertedOrUpdated = upserts,
                rowsAfter = repository.tracksForVolume(volumeIdentity).size,
                libraryCount = repository.tracksForVolume(volumeIdentity).size
            )
            lastDiagnostics = diagnostics
            return interrupted(enumerated.files.size, processed, diagnostics)
        }

        val folderRelative = MusicFolderPaths.relativeToVolume(volumeRoot, folderAbsolute) ?: ""
        repository.removeConfirmedMissing(volumeIdentity, folderRelative, seen)

        val after = repository.tracksForVolume(volumeIdentity)
        diagnostics = diagnostics.copy(
            metadataOk = metadataOk,
            metadataFailed = metadataFailed,
            rowsInsertedOrUpdated = upserts,
            rowsAfter = after.size,
            libraryCount = after.size,
            acceptedAudioFiles = enumerated.acceptedAudioFiles
        )
        lastDiagnostics = diagnostics

        ScannerLog.i(
            "ROOM_RESULT rows before=${diagnostics.rowsBefore} " +
                "rows inserted/updated=$upserts rows after=${after.size}"
        )
        ScannerLog.i("LIBRARY_RESULT volumeIdentity=$volumeIdentity track count=${after.size}")
        ScannerLog.i(diagnostics.formatSummary().replace('\n', ' '))

        emit(ScanPhase.COMPLETE, processed, enumerated.files.size, volumeIdentity, diagnostics)
        return ScanOutcome(ScanPhase.COMPLETE, enumerated.files.size, processed)
    }

    private fun processFile(
        volumeIdentity: String,
        volumeRoot: String,
        file: DiscoveredFile,
        existing: AudioTrack?
    ): ProcessResult {
        val storedAbsolute = MusicFolderPaths.join(volumeRoot, file.relativePath)
        val readPath = file.absolutePath?.takeIf { it.isNotBlank() } ?: storedAbsolute
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
                    absolutePath = storedAbsolute,
                    lastVerifiedAt = clock.now(),
                    updatedAt = clock.now(),
                    missingConfirmed = false
                )
            )
            return ProcessResult(upserts = 1, metadataSuccess = null)
        }

        val discovered = baseTrack(volumeIdentity, storedAbsolute, file, existing).copy(
            scanState = ScanState.DISCOVERED,
            metadataState = existing?.metadataState ?: MetadataState.METADATA_PENDING
        )
        repository.upsert(discovered)

        val first = probe.snapshot(readPath)
        if (first == null || !first.exists) {
            ScannerLog.w("snapshot missing after discover filename=${file.filename}")
            return ProcessResult(upserts = 1, metadataSuccess = null)
        }
        if (stabilityWaitMs > 0L) {
            sleeper.sleep(stabilityWaitMs)
        }
        if (shouldAbort()) {
            return ProcessResult(upserts = 1, metadataSuccess = null)
        }
        val second = probe.snapshot(readPath)
        if (second == null || !second.exists) {
            ScannerLog.w("second snapshot missing filename=${file.filename}")
            return ProcessResult(upserts = 1, metadataSuccess = null)
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
            ScannerLog.i("WAITING_STABLE filename=${file.filename} size ${first.size}->${second.size}")
            return ProcessResult(upserts = 2, metadataSuccess = null)
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
            metadataReader.read(readPath)
        } catch (error: Exception) {
            ScannerLog.error("metadataReader ${file.filename}", error)
            MetadataResult(
                success = false,
                errorClass = error.javaClass.name,
                errorMessage = error.message
            )
        }

        if (shouldAbort()) {
            return ProcessResult(upserts = 2, metadataSuccess = null)
        }

        val fallbackTitle = FilenameTitleParser.titleFromFilename(file.filename)

        if (!meta.success) {
            ScannerLog.i(
                "METADATA_RESULT filename=${file.filename} failure duration=null " +
                    "class=${meta.errorClass ?: "-"} message=${meta.errorMessage ?: "-"}"
            )
            if (meta.errorClass != null || meta.errorMessage != null) {
                ScannerLog.error(
                    "metadata ${file.filename}",
                    meta.errorClass,
                    meta.errorMessage
                )
            }
            // Metadata is enrichment. The file remains a visible, playable-attempt library row.
            repository.upsert(
                verifying.copy(
                    title = fallbackTitle,
                    artist = existing?.artist.orEmpty(),
                    album = existing?.album.orEmpty(),
                    scanState = ScanState.READY,
                    metadataState = MetadataState.UNVERIFIED,
                    playableState = PlayableState.UNKNOWN,
                    lastVerifiedAt = clock.now(),
                    updatedAt = clock.now(),
                    missingConfirmed = false
                )
            )
            return ProcessResult(upserts = 3, metadataSuccess = false)
        }

        ScannerLog.i(
            "METADATA_RESULT filename=${file.filename} success duration=${meta.durationMs ?: "-"}"
        )
        val title = meta.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
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
        return ProcessResult(upserts = 3, metadataSuccess = true)
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

    private fun interrupted(total: Int, processed: Int, diagnostics: ScanDiagnostics): ScanOutcome {
        lastDiagnostics = diagnostics
        emit(ScanPhase.INTERRUPTED, processed, total, diagnostics.volumeIdentity, diagnostics)
        return ScanOutcome(ScanPhase.INTERRUPTED, total, processed)
    }

    private fun emit(
        phase: ScanPhase,
        processed: Int = 0,
        total: Int = 0,
        volumeIdentity: String? = null,
        diagnostics: ScanDiagnostics = lastDiagnostics
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
                indexedCount = tracks.size,
                diagnostics = diagnostics.copy(libraryCount = tracks.size)
            )
        )
    }

    private fun fileFlag(block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (error: Exception) {
            ScannerLog.error("fileFlag", error)
            false
        }
    }

    private data class ProcessResult(
        val upserts: Int,
        val metadataSuccess: Boolean?
    )
}
