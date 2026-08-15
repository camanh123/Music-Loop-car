package com.musicloop.car.library

import com.musicloop.car.database.LibraryRepository
import com.musicloop.car.database.MediaItemEntity
import com.musicloop.car.database.ScanStatus
import com.musicloop.car.storage.EnumeratedMediaFile
import com.musicloop.car.storage.LibraryScanPolicy
import com.musicloop.car.storage.MediaEnumerator
import com.musicloop.car.storage.VolumeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Incremental USB library scanner. Runs on [Dispatchers.IO], never writes to USB,
 * and isolates per-file failures so one bad media file cannot abort the scan.
 */
class LibraryMediaScanner(
    private val repository: LibraryRepository,
    private val metadataReader: MetadataReader,
    private val enumerator: MediaEnumerator = MediaEnumerator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val batchSize: Int = LibraryScanPolicy.BATCH_SIZE,
    private val maxDepth: Int = LibraryScanPolicy.MAX_DEPTH,
    private val maxFiles: Int = LibraryScanPolicy.MAX_FILES,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun scanVolume(
        snapshot: VolumeSnapshot,
        onProgress: (ScanProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): ScanOutcome = withContext(ioDispatcher) {
        if (!snapshot.scannable) {
            return@withContext ScanOutcome.FAILED
        }
        val rootPath = snapshot.rootPath ?: return@withContext ScanOutcome.FAILED
        val root = File(rootPath)
        val volumeId = snapshot.volumeId
        val existing = try {
            repository.mediaForVolume(volumeId).associateBy { it.relativePath }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }

        val enumerated = try {
            enumerator.collect(
                root = root,
                maxDepth = maxDepth,
                maxFiles = maxFiles,
                isCancelled = isCancelled
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext ScanOutcome.FAILED
        }

        if (enumerated.rootVanished) {
            return@withContext ScanOutcome.VOLUME_OFFLINE
        }
        if (enumerated.cancelled || isCancelled()) {
            return@withContext ScanOutcome.CANCELLED
        }

        val discovered = enumerated.files
        val total = discovered.size
        onProgress(ScanProgress(scanned = 0, total = total))

        val pending = mutableListOf<MediaItemEntity>()
        val seen = HashSet<String>(total)
        var processed = 0

        try {
            for (file in discovered) {
                ensureActive()
                if (isCancelled()) {
                    return@withContext ScanOutcome.CANCELLED
                }
                if (rootVanished(root)) {
                    return@withContext ScanOutcome.VOLUME_OFFLINE
                }
                seen += file.relativePath
                val previous = existing[file.relativePath]
                val unchanged = previous != null &&
                    previous.sizeBytes == file.sizeBytes &&
                    previous.modifiedTime == file.modifiedTime
                if (unchanged && previous != null && previous.scanStatus != ScanStatus.STALE) {
                    processed += 1
                    onProgress(
                        ScanProgress(
                            scanned = processed,
                            total = total,
                            currentName = file.fileName
                        )
                    )
                    continue
                }
                pending += resolveItem(volumeId, file, previous)
                processed += 1
                if (pending.size >= batchSize) {
                    repository.upsertMedia(pending.toList())
                    pending.clear()
                }
                onProgress(
                    ScanProgress(
                        scanned = processed,
                        total = total,
                        currentName = file.fileName
                    )
                )
            }
            if (pending.isNotEmpty()) {
                repository.upsertMedia(pending.toList())
                pending.clear()
            }
            if (isCancelled() || enumerated.cancelled) {
                return@withContext ScanOutcome.CANCELLED
            }
            if (rootVanished(root)) {
                return@withContext ScanOutcome.VOLUME_OFFLINE
            }
            val staleIds = existing.values
                .asSequence()
                .filter { it.relativePath !in seen }
                .map { it.id }
                .filter { it != 0L }
                .toList()
            repository.markStale(staleIds, now())
            onProgress(ScanProgress(scanned = processed, total = total))
            ScanOutcome.COMPLETED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ScanOutcome.FAILED
        }
    }

    private fun resolveItem(
        volumeId: String,
        file: EnumeratedMediaFile,
        previous: MediaItemEntity?
    ): MediaItemEntity {
        val unchanged = previous != null &&
            previous.sizeBytes == file.sizeBytes &&
            previous.modifiedTime == file.modifiedTime
        if (unchanged && previous != null) {
            return previous.copy(
                fileName = file.fileName,
                extension = file.extension,
                mediaType = file.mediaType.name,
                lastScannedAt = now(),
                scanStatus = if (previous.scanStatus == ScanStatus.STALE) {
                    ScanStatus.COMPLETE
                } else {
                    previous.scanStatus
                }
            )
        }
        val metadata = readMetadata(file.file)
        val status = if (metadata.complete) ScanStatus.COMPLETE else ScanStatus.PARTIAL
        return MediaItemEntity(
            id = previous?.id ?: 0L,
            volumeId = volumeId,
            relativePath = file.relativePath,
            fileName = file.fileName,
            extension = file.extension,
            mediaType = file.mediaType.name,
            sizeBytes = file.sizeBytes,
            modifiedTime = file.modifiedTime,
            durationMs = metadata.durationMs,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            width = metadata.width,
            height = metadata.height,
            scanStatus = status,
            lastScannedAt = now()
        )
    }

    private fun readMetadata(file: File): ExtractedMetadata {
        return try {
            metadataReader.read(file)
        } catch (_: Exception) {
            ExtractedMetadata.PARTIAL
        }
    }

    private fun rootVanished(root: File): Boolean {
        return try {
            !root.exists() || !root.canRead()
        } catch (_: Exception) {
            true
        }
    }
}
