package com.musicloop.car.scanner

import com.musicloop.car.storage.MainPoster
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs [MusicScanner] off the main thread with cancel support.
 */
class MusicScanController(
    private val repository: TrackRepository,
    private val metadataReader: MetadataReader,
    private val ioExecutor: Executor,
    private val mainPoster: MainPoster,
    private val listener: (ScanProgress) -> Unit
) {

    private val cancel = AtomicBoolean(false)
    private val volumeRoot = AtomicReference<String?>(null)
    private var lastProgress: ScanProgress = ScanProgress()

    fun cancel() {
        cancel.set(true)
        volumeRoot.set(null)
    }

    fun currentProgress(): ScanProgress = lastProgress

    fun startScan(volumeIdentity: String, volumeRootPath: String, folderAbsolute: String) {
        cancel.set(false)
        volumeRoot.set(volumeRootPath)
        emit(ScanProgress(phase = ScanPhase.ENUMERATING))
        ioExecutor.execute {
            ScannerLog.i("SCAN_START")
            ScannerLog.i("volumeIdentity=$volumeIdentity")
            ScannerLog.i("volumeRoot=$volumeRootPath")
            ScannerLog.i("folderAbsolute=$folderAbsolute")
            logFolderFlags(folderAbsolute)
            val scanner = MusicScanner(
                probe = FilesystemAudioFileProbe(
                    volumeRootProvider = { volumeRoot.get() },
                    isCancelled = { cancel.get() }
                ),
                metadataReader = metadataReader,
                repository = repository,
                clock = ScanClock { System.currentTimeMillis() },
                sleeper = ScanSleeper { duration ->
                    try {
                        Thread.sleep(duration)
                    } catch (error: InterruptedException) {
                        ScannerLog.error("stability wait interrupted", error)
                        Thread.currentThread().interrupt()
                    }
                },
                isCancelled = { cancel.get() },
                onProgress = { progress -> emit(progress) }
            )
            try {
                scanner.scan(volumeIdentity, volumeRootPath, folderAbsolute)
            } catch (error: Exception) {
                ScannerLog.error("scan", error)
                emit(
                    lastProgress.copy(phase = ScanPhase.INTERRUPTED)
                )
            }
        }
    }

    private fun logFolderFlags(folderAbsolute: String) {
        val folder = File(folderAbsolute)
        try {
            ScannerLog.i("folder exists=${folder.exists()}")
        } catch (error: Exception) {
            ScannerLog.error("folder.exists", error)
        }
        try {
            ScannerLog.i("folder isDirectory=${folder.isDirectory}")
        } catch (error: Exception) {
            ScannerLog.error("folder.isDirectory", error)
        }
        try {
            ScannerLog.i("folder canRead=${folder.canRead()}")
        } catch (error: Exception) {
            ScannerLog.error("folder.canRead", error)
        }
        try {
            ScannerLog.i("folder absolutePath=${folder.absolutePath}")
        } catch (error: Exception) {
            ScannerLog.error("folder.absolutePath", error)
            ScannerLog.i("folder absolutePath=$folderAbsolute")
        }
    }

    private fun emit(progress: ScanProgress) {
        lastProgress = progress
        mainPoster.post { listener(progress) }
    }
}
