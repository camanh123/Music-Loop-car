package com.musicloop.car.playback

import com.musicloop.car.database.RoomTrackRepository
import com.musicloop.car.player.AutoPlayPolicy
import com.musicloop.car.player.PlaybackController
import com.musicloop.car.player.PlaybackPathResolver
import com.musicloop.car.player.PlaybackSnapshot
import com.musicloop.car.player.PlayerState
import com.musicloop.car.player.QueueItem
import com.musicloop.car.player.QueueSource
import com.musicloop.car.player.TrackFileAccess
import com.musicloop.car.scanner.AudioTrack
import com.musicloop.car.scanner.MusicScanController
import com.musicloop.car.scanner.ScanProgress
import com.musicloop.car.scanner.ScannerLog
import com.musicloop.car.settings.AppSettingsRepository
import com.musicloop.car.storage.UsbCoordinator
import com.musicloop.car.ui.library.SongRow
import com.musicloop.car.ui.state.UsbStatus
import com.musicloop.car.ui.state.UsbUiState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns USB restore, incremental scan, and playback for the foreground service.
 * MainActivity binds to the service; it must not create a second MediaPlayer.
 */
class MusicSession(
    val playback: PlaybackController,
    val coordinator: UsbCoordinator,
    private val scanController: MusicScanController,
    private val tracks: RoomTrackRepository,
    private val settings: AppSettingsRepository,
    private val fileAccess: TrackFileAccess = PlaybackPathResolver,
    private val hasReadPermission: () -> Boolean,
    private val ioExecutor: Executor,
    private val mainPoster: ( () -> Unit ) -> Unit,
    private val listener: Listener
) {
    interface Listener {
        fun onUsb(state: UsbUiState)
        fun onPlayback(snapshot: PlaybackSnapshot)
        fun onScan(progress: ScanProgress)
        fun onLibrary(tracks: List<AudioTrack>, volumeIdentity: String?)
    }

    private var bootStart: Boolean = false
    private val autoPlayAttempted = AtomicBoolean(false)
    private var scannedFolderKey: String? = null
    private var lastVolumeIdentity: String? = null
    var lastUsb: UsbUiState = UsbUiState(UsbStatus.WAITING_FOR_USB)
        private set
    var lastTracks: List<AudioTrack> = emptyList()
        private set

    fun setBootStart(enabled: Boolean) {
        bootStart = enabled
        if (enabled) {
            autoPlayAttempted.set(false)
        }
    }

    fun onUsbState(state: UsbUiState) {
        lastUsb = state
        listener.onUsb(state)
        if (state.volumeIdentity != null) {
            lastVolumeIdentity = state.volumeIdentity
        }
        if (state.status == UsbStatus.USB_READY &&
            hasReadPermission() &&
            state.resolvedAbsolutePath != null &&
            state.volumeIdentity != null &&
            state.volumeRootPath != null
        ) {
            playback.setVolumeContext(state.volumeIdentity, state.volumeRootPath, true)
            val key = "${state.volumeIdentity}|${state.resolvedAbsolutePath}"
            if (key != scannedFolderKey) {
                scannedFolderKey = key
                scanController.startScan(
                    state.volumeIdentity,
                    state.volumeRootPath,
                    state.resolvedAbsolutePath
                )
            }
            maybeAutoPlay()
        } else if (state.status == UsbStatus.USB_READY) {
            ScannerLog.w(
                "USB_READY but scan not started volumeIdentity=${state.volumeIdentity} " +
                    "volumeRoot=${state.volumeRootPath} folder=${state.resolvedAbsolutePath} " +
                    "permission=${hasReadPermission()}"
            )
        } else if (state.status == UsbStatus.USB_DISCONNECTED ||
            state.status == UsbStatus.USB_ERROR ||
            state.status == UsbStatus.FOLDER_NOT_FOUND
        ) {
            scanController.cancel()
            scannedFolderKey = null
            playback.onUsbDisconnected()
        } else if (state.status == UsbStatus.WAITING_FOR_USB && !state.usbPresent) {
            scanController.cancel()
            scannedFolderKey = null
            if (playback.snapshot().track != null) {
                playback.onUsbDisconnected()
            }
        }
        reloadLibrary(lastVolumeIdentity)
    }

    fun onScan(progress: ScanProgress) {
        listener.onScan(progress)
        reloadLibrary(lastVolumeIdentity)
    }

    fun onPlayback(snapshot: PlaybackSnapshot) {
        listener.onPlayback(snapshot)
    }

    fun playFromVisibleList(item: QueueItem, visible: List<QueueItem>, source: QueueSource, playlistId: Long?) {
        playback.setSourceQueue(visible, source, playlistId)
        playback.playUserSelected(item)
    }

    fun toggleFavorite(): Boolean? {
        val track = playback.snapshot().track ?: return null
        if (track.id <= 0L) {
            return null
        }
        val next = !track.favorite
        try {
            tracks.setFavorite(track.id, next)
        } catch (_: Exception) {
            return null
        }
        playback.updateCurrentFavorite(next)
        reloadLibrary(lastVolumeIdentity)
        return next
    }

    fun release() {
        scanController.cancel()
        playback.release()
        coordinator.stop()
    }

    private fun maybeAutoPlay() {
        val snapshot = playback.snapshot()
        val track = snapshot.track
        val readable = track != null &&
            fileAccess.resolveReadable(lastUsb.volumeRootPath, track.relativePath) != null
        val should = AutoPlayPolicy.shouldStart(
            autoPlayEnabled = settings.autoPlayOnBoot(),
            bootStart = bootStart,
            usbReady = lastUsb.status == UsbStatus.USB_READY,
            track = track,
            readable = readable,
            alreadyAttempted = autoPlayAttempted.get()
        )
        if (!should) {
            return
        }
        autoPlayAttempted.set(true)
        bootStart = false
        try {
            playback.tryStartPlayback()
        } catch (_: Exception) {
            PlaybackSafe.ignore()
        }
    }

    private fun reloadLibrary(volumeIdentity: String?) {
        if (volumeIdentity.isNullOrBlank()) {
            lastTracks = emptyList()
            playback.setSourceQueue(emptyList(), QueueSource.ALL_SONGS, null)
            listener.onLibrary(emptyList(), null)
            return
        }
        ioExecutor.execute {
            val loaded = try {
                tracks.tracksForVolume(volumeIdentity)
            } catch (error: Exception) {
                ScannerLog.error("tracksForVolume", error)
                emptyList()
            }
            ScannerLog.i("LIBRARY_RESULT volumeIdentity=$volumeIdentity track count=${loaded.size}")
            mainPoster {
                lastTracks = loaded
                listener.onLibrary(loaded, volumeIdentity)
                val currentSource = playback.snapshot().queueSource
                if (currentSource == QueueSource.ALL_SONGS) {
                    playback.setSourceQueue(loaded.map { SongRow.from(it).toQueueItem() }, QueueSource.ALL_SONGS, null)
                }
                maybeAutoPlay()
            }
        }
    }

    private object PlaybackSafe {
        fun ignore() = Unit
    }
}
