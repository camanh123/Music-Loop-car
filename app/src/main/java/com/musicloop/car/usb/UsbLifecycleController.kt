package com.musicloop.car.usb

import android.content.Intent
import android.util.Log
import com.musicloop.car.database.LibraryRepository
import com.musicloop.car.database.LibrarySnapshot
import com.musicloop.car.database.UsbVolumeEntity
import com.musicloop.car.library.LibraryMediaScanner
import com.musicloop.car.library.LibraryUiState
import com.musicloop.car.library.ScanOutcome
import com.musicloop.car.library.ScanProgress
import com.musicloop.car.library.ScanUiState
import com.musicloop.car.library.toMediaListRow
import com.musicloop.car.storage.LibraryScanPolicy
import com.musicloop.car.storage.VolumeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB mount/unmount/remount coordinator.
 *
 * BroadcastReceiver events are triggers only. [snapshotVolumes] (StorageManager)
 * is the source of truth for whether a volume is actually mounted and readable.
 *
 * On remount, cached Room rows are published immediately, then an incremental
 * scan runs on the controller scope (Dispatchers.IO in production).
 */
class UsbLifecycleController(
    private val snapshotVolumes: () -> List<VolumeSnapshot>,
    private val scanner: LibraryMediaScanner,
    private val repository: LibraryRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val elapsedNow: () -> Long = now,
    private val onOnlineVolumesChanged: (Set<String>) -> Unit = {}
) {
    private val _uiState = MutableStateFlow(LibraryUiState(statusMessage = "Idle"))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    var lastRestoreReport: UsbRestoreReport? = null
        private set

    private val mutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)
    private var scanJob: Job? = null

    init {
        scope.launch {
            repository.observeLibrary().collect { snapshot ->
                applyLibrarySnapshot(snapshot)
            }
        }
    }

    fun start() {
        reconcile(autoScan = true)
    }

    fun scanOrRescan() {
        cancelRequested.set(false)
        reconcile(autoScan = true)
    }

    fun onBroadcast(action: String?) {
        when (action) {
            Intent.ACTION_MEDIA_MOUNTED,
            Intent.ACTION_MEDIA_CHECKING -> {
                cancelRequested.set(false)
                reconcile(autoScan = true)
            }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_REMOVED,
            Intent.ACTION_MEDIA_BAD_REMOVAL -> {
                cancelRequested.set(true)
                scanJob?.cancel()
                reconcile(autoScan = false)
            }
        }
    }

    private fun reconcile(autoScan: Boolean) {
        scope.launch {
            var shouldScan = false
            mutex.withLock {
                val restoreStartedAt = elapsedNow()
                _uiState.update {
                    it.copy(
                        scanState = ScanUiState.DETECTING_USB,
                        errorMessage = null,
                        statusMessage = "Detecting USB"
                    )
                }
                val snapshots = snapshotVolumesSafe()
                val present = snapshots.filter { it.presentMountedRemovable }
                val scannable = snapshots.filter { it.scannable }
                applyVolumeRecords(present)
                if (present.isEmpty()) {
                    publishOffline()
                    return@withLock
                }
                val active = scannable.firstOrNull() ?: present.first()
                val cached = try {
                    repository.mediaForVolume(active.volumeId)
                } catch (_: Exception) {
                    emptyList()
                }
                val rows = cached.take(LibraryScanPolicy.UI_LIST_LIMIT).map { it.toMediaListRow() }
                val visibleMs = (elapsedNow() - restoreStartedAt).coerceAtLeast(0L)
                lastRestoreReport = UsbRestoreReport(
                    volumeId = active.volumeId,
                    cachedItems = cached.size,
                    libraryVisibleMs = visibleMs,
                    scanCompletedMs = null,
                    restoreStartedAtElapsed = restoreStartedAt,
                )
                logRestore("volumeId=${active.volumeId} cachedItems=${cached.size} libraryVisibleMs=$visibleMs scanCompletedMs=-")
                _uiState.update {
                    it.copy(
                        usbOnline = true,
                        volumeDescription = active.description,
                        volumeId = active.volumeId,
                        lastKnownRootPath = active.rootPath,
                        media = if (rows.isNotEmpty()) rows else it.media,
                        audioCount = cached.count { item -> item.mediaType == "AUDIO" },
                        videoCount = cached.count { item -> item.mediaType == "VIDEO" },
                        totalCount = cached.size,
                        scanState = if (autoScan && scannable.isNotEmpty()) {
                            ScanUiState.SCANNING
                        } else {
                            ScanUiState.IDLE
                        },
                        statusMessage = when {
                            autoScan && scannable.isNotEmpty() && cached.isNotEmpty() ->
                                UPDATING_LIBRARY
                            autoScan && scannable.isNotEmpty() -> "Scanning"
                            else -> "USB Online"
                        }
                    )
                }
                shouldScan = autoScan && scannable.isNotEmpty() && !cancelRequested.get()
            }
            if (shouldScan) {
                startScan()
            }
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            val updating = (lastRestoreReport?.cachedItems ?: 0) > 0
            _uiState.update {
                it.copy(
                    scanState = ScanUiState.SCANNING,
                    statusMessage = if (updating) UPDATING_LIBRARY else "Scanning",
                    progress = ScanProgress(),
                    errorMessage = null
                )
            }
            val snapshots = try {
                snapshotVolumes().filter { it.scannable }
            } catch (_: Exception) {
                emptyList()
            }
            if (snapshots.isEmpty()) {
                mutex.withLock {
                    val present = snapshotVolumesSafe().filter { it.presentMountedRemovable }
                    applyVolumeRecords(present)
                    if (present.isEmpty()) {
                        publishOffline()
                    } else {
                        _uiState.update {
                            it.copy(
                                scanState = ScanUiState.IDLE,
                                usbOnline = true,
                                statusMessage = "USB Online"
                            )
                        }
                    }
                }
                return@launch
            }
            var lastOutcome = ScanOutcome.COMPLETED
            for (snapshot in snapshots) {
                if (cancelRequested.get() || !isActive) {
                    lastOutcome = ScanOutcome.CANCELLED
                    break
                }
                lastOutcome = try {
                    scanner.scanVolume(
                        snapshot = snapshot,
                        onProgress = { progress ->
                            _uiState.update {
                                it.copy(
                                    scanState = ScanUiState.SCANNING,
                                    progress = progress,
                                    statusMessage = progressLabel(progress)
                                )
                            }
                        },
                        isCancelled = { cancelRequested.get() || !isActive }
                    )
                } catch (_: kotlinx.coroutines.CancellationException) {
                    ScanOutcome.CANCELLED
                } catch (_: Exception) {
                    ScanOutcome.FAILED
                }
                if (lastOutcome != ScanOutcome.COMPLETED) {
                    break
                }
            }
            mutex.withLock {
                when (lastOutcome) {
                    ScanOutcome.COMPLETED -> {
                        val report = lastRestoreReport
                        val startedAt = report?.restoreStartedAtElapsed ?: elapsedNow()
                        val completedMs = (elapsedNow() - startedAt).coerceAtLeast(0L)
                        val visibleMs = report?.libraryVisibleMs ?: 0L
                        lastRestoreReport = (report ?: UsbRestoreReport(
                            volumeId = _uiState.value.volumeId,
                            cachedItems = 0,
                            libraryVisibleMs = visibleMs,
                            restoreStartedAtElapsed = startedAt,
                        )).copy(scanCompletedMs = completedMs)
                        logRestore(
                            "volumeId=${_uiState.value.volumeId} cachedItems=${lastRestoreReport?.cachedItems ?: 0} libraryVisibleMs=$visibleMs scanCompletedMs=$completedMs"
                        )
                        _uiState.update {
                            it.copy(
                                scanState = ScanUiState.COMPLETED,
                                usbOnline = true,
                                statusMessage = "Completed"
                            )
                        }
                    }
                    ScanOutcome.VOLUME_OFFLINE, ScanOutcome.CANCELLED -> {
                        val present = snapshotVolumesSafe().filter { it.presentMountedRemovable }
                        applyVolumeRecords(present)
                        if (present.isEmpty()) {
                            publishOffline()
                        } else {
                            _uiState.update {
                                it.copy(
                                    scanState = ScanUiState.IDLE,
                                    usbOnline = true,
                                    statusMessage = "USB Online",
                                    progress = ScanProgress()
                                )
                            }
                        }
                    }
                    ScanOutcome.FAILED -> _uiState.update {
                        it.copy(
                            scanState = ScanUiState.FAILED,
                            statusMessage = "Failed",
                            errorMessage = "Scan failed"
                        )
                    }
                }
            }
        }
    }

    private suspend fun applyVolumeRecords(present: List<VolumeSnapshot>) {
        val at = now()
        val onlineIds = present.map { it.volumeId }.toSet()
        val known = try {
            repository.getAllVolumes()
        } catch (_: Exception) {
            emptyList()
        }
        for (volume in known) {
            if (volume.volumeId !in onlineIds && volume.isOnline) {
                try {
                    repository.upsertVolume(volume.copy(isOnline = false, updatedAt = at))
                } catch (_: Exception) {
                    // Keep going; UI must not crash if one row fails.
                }
            }
        }
        for (snapshot in present) {
            val existing = known.firstOrNull { it.volumeId == snapshot.volumeId }
            try {
                repository.upsertVolume(
                    UsbVolumeEntity(
                        volumeId = snapshot.volumeId,
                        description = snapshot.description,
                        uuid = snapshot.uuid,
                        lastKnownRootPath = snapshot.rootPath,
                        isOnline = true,
                        lastSeenAt = at,
                        createdAt = existing?.createdAt ?: at,
                        updatedAt = at
                    )
                )
            } catch (_: Exception) {
                // Isolated per volume.
            }
        }
        try {
            onOnlineVolumesChanged(onlineIds)
        } catch (_: Exception) {
            // Playback notification must not break USB lifecycle.
        }
    }

    private fun snapshotVolumesSafe(): List<VolumeSnapshot> {
        return try {
            snapshotVolumes()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun publishOffline() {
        val current = _uiState.value
        _uiState.update {
            it.copy(
                scanState = ScanUiState.USB_OFFLINE,
                usbOnline = false,
                progress = ScanProgress(),
                statusMessage = "USB Offline",
                volumeDescription = current.volumeDescription,
                volumeId = current.volumeId,
                lastKnownRootPath = current.lastKnownRootPath
            )
        }
        try {
            onOnlineVolumesChanged(emptySet())
        } catch (_: Exception) {
            // Playback notification must not break USB lifecycle.
        }
    }

    private fun applyLibrarySnapshot(snapshot: LibrarySnapshot) {
        val online = snapshot.volumes.firstOrNull { it.isOnline } ?: snapshot.volumes.firstOrNull()
        val rows = snapshot.media.take(LibraryScanPolicy.UI_LIST_LIMIT).map { it.toMediaListRow() }
        _uiState.update { current ->
            current.copy(
                audioCount = snapshot.audioCount,
                videoCount = snapshot.videoCount,
                totalCount = snapshot.totalCount,
                media = rows,
                volumeDescription = if (current.volumeDescription.isNotBlank()) {
                    current.volumeDescription
                } else {
                    online?.description.orEmpty()
                },
                volumeId = current.volumeId.ifBlank { online?.volumeId.orEmpty() },
                lastKnownRootPath = current.lastKnownRootPath ?: online?.lastKnownRootPath
            )
        }
    }

    private fun progressLabel(progress: ScanProgress): String {
        val updating = (lastRestoreReport?.cachedItems ?: 0) > 0
        val prefix = if (updating) UPDATING_LIBRARY else "Scanning..."
        return if (progress.total > 0) {
            "$prefix ${progress.scanned} / ${progress.total}"
        } else {
            prefix
        }
    }

    private fun logRestore(message: String) {
        Log.i(RESTORE_TAG, message)
    }

    companion object {
        const val UPDATING_LIBRARY = "Đang cập nhật thư viện..."
        private const val RESTORE_TAG = "USB_RESTORE"
    }
}
