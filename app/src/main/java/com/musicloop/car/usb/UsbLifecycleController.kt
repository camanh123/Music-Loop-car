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
import kotlinx.coroutines.delay
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
 * Missing broadcasts do not prove the USB was removed. Polling and manual rescan
 * query StorageManager only — never hardcoded mount paths.
 */
class UsbLifecycleController(
    private val snapshotVolumes: () -> List<VolumeSnapshot>,
    private val scanner: LibraryMediaScanner,
    private val repository: LibraryRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val elapsedNow: () -> Long = now,
    private val pollIntervalMs: Long = UsbRecoveryPolicy.POLL_INTERVAL_MS,
    private val onOnlineVolumesChanged: (Set<String>) -> Unit = {}
) {
    private val _uiState = MutableStateFlow(LibraryUiState(statusMessage = "Idle"))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    var lastRestoreReport: UsbRestoreReport? = null
        private set

    var lastRecoveryAction: String? = null
        private set

    var scanStartCount: Int = 0
        private set

    var storageSnapshotCount: Int = 0
        private set

    private val mutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)
    private var scanJob: Job? = null
    private var pollJob: Job? = null
    private var foregroundPolling = false
    private var hadKnownVolume = false
    private var lastSeenVolumeIds: Set<String> = emptySet()
    private var lastOnlineVolumeIds: Set<String> = emptySet()

    init {
        scope.launch {
            repository.observeLibrary().collect { snapshot ->
                applyLibrarySnapshot(snapshot)
            }
        }
    }

    fun start() {
        reconcile(autoScan = true, reason = "START")
    }

    fun scanOrRescan() {
        manualRescan()
    }

    fun manualRescan() {
        cancelRequested.set(false)
        scanJob?.cancel()
        scanJob = null
        reconcile(autoScan = true, reason = "MANUAL_RESCAN", forceUi = true)
    }

    fun setForegroundPolling(enabled: Boolean) {
        foregroundPolling = enabled
        if (enabled) {
            startPollingIfNeeded()
        } else {
            pollJob?.cancel()
            pollJob = null
        }
    }

    fun onBroadcast(action: String?) {
        when (action) {
            Intent.ACTION_MEDIA_MOUNTED,
            Intent.ACTION_MEDIA_CHECKING -> {
                cancelRequested.set(false)
                reconcile(autoScan = true, reason = "BROADCAST_MOUNTED")
            }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_REMOVED,
            Intent.ACTION_MEDIA_BAD_REMOVAL -> {
                reconcile(autoScan = false, reason = "BROADCAST_UNMOUNTED")
            }
        }
    }

    private fun reconcile(autoScan: Boolean, reason: String, forceUi: Boolean = false) {
        scope.launch {
            var shouldScan = false
            mutex.withLock {
                lastRecoveryAction = reason
                val restoreStartedAt = elapsedNow()
                if (reason != "POLL") {
                    _uiState.update {
                        it.copy(
                            scanState = ScanUiState.DETECTING_USB,
                            errorMessage = null,
                            statusMessage = "Detecting USB"
                        )
                    }
                }
                val snapshots = snapshotVolumesSafe()
                val present = snapshots.filter { it.presentMountedRemovable }
                val scannable = snapshots.filter { it.scannable }
                logRecovery(
                    "action=$reason volumes=${snapshots.size} removableMounted=${present.size}"
                )
                val presentIds = present.map { it.volumeId }.toSet()
                if (reason.startsWith("BROADCAST") && presentIds == lastSeenVolumeIds &&
                    present.isNotEmpty() == _uiState.value.usbOnline &&
                    (scanJob?.isActive == true || _uiState.value.usbHostState == UsbHostState.USB_READY ||
                        _uiState.value.usbHostState == UsbHostState.USB_ONLINE ||
                        _uiState.value.usbHostState == UsbHostState.USB_SCANNING)
                ) {
                    if (present.isNotEmpty()) {
                        logRecovery("action=$reason volumeDetected=true ignored=unchanged")
                    }
                    return@withLock
                }
                if (reason == "POLL" && presentIds.isNotEmpty() &&
                    presentIds == lastOnlineVolumeIds &&
                    _uiState.value.usbOnline &&
                    scanJob?.isActive != true
                ) {
                    logRecovery("action=POLL volumeDetected=true ignored=already_online")
                    return@withLock
                }
                if (present.isEmpty()) {
                    if (scanJob?.isActive == true) {
                        cancelRequested.set(true)
                        scanJob?.cancel()
                    }
                    applyVolumeRecords(present)
                    lastSeenVolumeIds = emptySet()
                    lastOnlineVolumeIds = emptySet()
                    publishNotDetected(forceUi = forceUi || reason == "MANUAL_RESCAN" || reason.startsWith("BROADCAST"))
                    startPollingIfNeeded()
                    return@withLock
                }
                hadKnownVolume = true
                lastSeenVolumeIds = presentIds
                applyVolumeRecords(present)
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
                logRecovery(
                    "action=CACHE_RESTORE volumeId=${active.volumeId} cachedItems=${cached.size}"
                )
                val scanning = autoScan && scannable.isNotEmpty()
                _uiState.update {
                    it.copy(
                        usbOnline = true,
                        usbHostState = if (scanning) UsbHostState.USB_SCANNING else UsbHostState.USB_ONLINE,
                        volumeDescription = active.description,
                        volumeId = active.volumeId,
                        lastKnownRootPath = active.rootPath,
                        media = if (rows.isNotEmpty()) rows else it.media,
                        audioCount = cached.count { item -> item.mediaType == "AUDIO" },
                        videoCount = cached.count { item -> item.mediaType == "VIDEO" },
                        totalCount = cached.size,
                        scanState = if (scanning) ScanUiState.SCANNING else ScanUiState.IDLE,
                        diagnosticMessage = null,
                        errorMessage = null,
                        statusMessage = when {
                            scanning && cached.isNotEmpty() -> UsbRecoveryPolicy.CONNECTED_UPDATING
                            scanning -> "Scanning"
                            else -> "USB Online"
                        }
                    )
                }
                shouldScan = scanning && !cancelRequested.get()
                if (scanning && scanJob?.isActive == true && reason != "MANUAL_RESCAN") {
                    logRecovery("action=INCREMENTAL_SCAN skipped=in_flight")
                    shouldScan = false
                }
            }
            if (shouldScan) {
                startScan()
            }
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        scanStartCount += 1
        scanJob = scope.launch {
            val updating = (lastRestoreReport?.cachedItems ?: 0) > 0
            _uiState.update {
                it.copy(
                    scanState = ScanUiState.SCANNING,
                    usbHostState = UsbHostState.USB_SCANNING,
                    usbOnline = true,
                    diagnosticMessage = null,
                    statusMessage = if (updating) UsbRecoveryPolicy.CONNECTED_UPDATING else "Scanning",
                    progress = ScanProgress(),
                    errorMessage = null
                )
            }
            val snapshots = try {
                snapshotVolumesSafe().filter { it.scannable }
            } catch (_: Exception) {
                emptyList()
            }
            if (snapshots.isEmpty()) {
                mutex.withLock {
                    val present = snapshotVolumesSafe().filter { it.presentMountedRemovable }
                    applyVolumeRecords(present)
                    if (present.isEmpty()) {
                        publishNotDetected(forceUi = true)
                        startPollingIfNeeded()
                    } else {
                        publishOnlineIdle()
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
                                    usbHostState = UsbHostState.USB_SCANNING,
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
                if (lastOutcome == ScanOutcome.COMPLETED) {
                    val report = scanner.lastIncrementalReport
                    logRecovery(
                        "action=INCREMENTAL_SCAN volumeId=${report.volumeId} changed=${report.changed} new=${report.newItems} stale=${report.stale} unchanged=${report.unchanged}"
                    )
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
                        lastOnlineVolumeIds = lastSeenVolumeIds
                        _uiState.update {
                            it.copy(
                                scanState = ScanUiState.COMPLETED,
                                usbHostState = UsbHostState.USB_READY,
                                usbOnline = true,
                                diagnosticMessage = null,
                                statusMessage = "Completed"
                            )
                        }
                    }
                    ScanOutcome.VOLUME_OFFLINE, ScanOutcome.CANCELLED -> {
                        val present = snapshotVolumesSafe().filter { it.presentMountedRemovable }
                        applyVolumeRecords(present)
                        if (present.isEmpty()) {
                            publishNotDetected(forceUi = true)
                            startPollingIfNeeded()
                        } else {
                            publishOnlineIdle()
                        }
                    }
                    ScanOutcome.FAILED -> _uiState.update {
                        it.copy(
                            scanState = ScanUiState.FAILED,
                            usbHostState = UsbHostState.USB_ERROR,
                            usbOnline = true,
                            statusMessage = "Failed",
                            errorMessage = "Scan failed"
                        )
                    }
                }
            }
        }
    }

    private fun startPollingIfNeeded() {
        if (!foregroundPolling) {
            return
        }
        if (!isAbsentState(_uiState.value.usbHostState)) {
            return
        }
        if (pollJob?.isActive == true) {
            return
        }
        pollJob = scope.launch {
            while (isActive && foregroundPolling && isAbsentState(_uiState.value.usbHostState)) {
                delay(pollIntervalMs)
                if (!isActive || !foregroundPolling) {
                    break
                }
                pollStorageManager()
            }
        }
    }

    private suspend fun pollStorageManager() {
        val snapshots = snapshotVolumesSafe()
        val present = snapshots.filter { it.presentMountedRemovable }
        val detected = present.isNotEmpty()
        val active = present.firstOrNull()
        if (detected && active != null) {
            logRecovery(
                "action=POLL volumeDetected=true volumeId=${active.volumeId} root=${active.rootPath ?: "-"} state=ONLINE"
            )
            reconcile(autoScan = true, reason = "POLL")
        } else {
            logRecovery("action=POLL volumeDetected=false")
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
        var changed = false
        for (volume in known) {
            if (volume.volumeId !in onlineIds && volume.isOnline) {
                try {
                    repository.upsertVolume(volume.copy(isOnline = false, updatedAt = at))
                    changed = true
                } catch (_: Exception) {
                    // Keep going; UI must not crash if one row fails.
                }
            }
        }
        for (snapshot in present) {
            val existing = known.firstOrNull { it.volumeId == snapshot.volumeId }
            val sameOnline = existing != null &&
                existing.isOnline &&
                existing.lastKnownRootPath == snapshot.rootPath &&
                existing.description == snapshot.description
            if (sameOnline) {
                continue
            }
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
                changed = true
            } catch (_: Exception) {
                // Isolated per volume.
            }
        }
        if (changed || onlineIds != lastOnlineVolumeIds) {
            try {
                onOnlineVolumesChanged(onlineIds)
            } catch (_: Exception) {
                // Playback notification must not break USB lifecycle.
            }
        }
    }

    private fun snapshotVolumesSafe(): List<VolumeSnapshot> {
        storageSnapshotCount += 1
        return try {
            snapshotVolumes()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun publishNotDetected(forceUi: Boolean) {
        val host = UsbPresenceClassifier.classify(
            removableMounted = 0,
            scanning = false,
            scanFailed = false,
            scanCompleted = false,
            hadKnownVolume = hadKnownVolume
        )
        val current = _uiState.value
        if (!forceUi && current.usbHostState == host && !current.usbOnline) {
            return
        }
        val diagnostic = UsbRecoveryPolicy.NOT_DETECTED_USER + "\n" + UsbRecoveryPolicy.NOT_DETECTED_OS
        _uiState.update {
            it.copy(
                scanState = ScanUiState.USB_OFFLINE,
                usbHostState = host,
                usbOnline = false,
                progress = ScanProgress(),
                statusMessage = if (host == UsbHostState.USB_NOT_DETECTED) {
                    UsbRecoveryPolicy.ANDROID_USB_NOT_DETECTED
                } else {
                    "USB Offline"
                },
                diagnosticMessage = diagnostic,
                errorMessage = null,
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

    private fun publishOnlineIdle() {
        lastOnlineVolumeIds = lastSeenVolumeIds
        _uiState.update {
            it.copy(
                scanState = ScanUiState.IDLE,
                usbHostState = UsbHostState.USB_ONLINE,
                usbOnline = true,
                diagnosticMessage = null,
                statusMessage = "USB Online",
                progress = ScanProgress()
            )
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
        val prefix = if (updating) UsbRecoveryPolicy.CONNECTED_UPDATING else "Scanning..."
        return if (progress.total > 0) {
            "$prefix ${progress.scanned} / ${progress.total}"
        } else {
            prefix
        }
    }

    private fun logRestore(message: String) {
        Log.i(RESTORE_TAG, message)
    }

    private fun logRecovery(message: String) {
        Log.i(RECOVERY_TAG, message)
    }

    private fun isAbsentState(state: UsbHostState): Boolean {
        return state == UsbHostState.USB_OFFLINE || state == UsbHostState.USB_NOT_DETECTED
    }

    companion object {
        const val UPDATING_LIBRARY = UsbRecoveryPolicy.CONNECTED_UPDATING
        private const val RESTORE_TAG = "USB_RESTORE"
        private const val RECOVERY_TAG = "USB_RECOVERY"
    }
}
