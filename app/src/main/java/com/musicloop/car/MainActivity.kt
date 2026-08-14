package com.musicloop.car

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.musicloop.car.database.RoomTrackRepository
import com.musicloop.car.databinding.ActivityMainBinding
import com.musicloop.car.player.AudioFocusHelper
import com.musicloop.car.player.MediaPlayerEngine
import com.musicloop.car.player.PlaybackController
import com.musicloop.car.player.PlaybackMessage
import com.musicloop.car.player.PlaybackPathResolver
import com.musicloop.car.player.PlaybackSnapshot
import com.musicloop.car.player.PlaybackStateStore
import com.musicloop.car.player.PlaybackTime
import com.musicloop.car.player.PlayerState
import com.musicloop.car.player.RepeatMode
import com.musicloop.car.scanner.AndroidMetadataReader
import com.musicloop.car.scanner.MusicScanController
import com.musicloop.car.scanner.ScanPhase
import com.musicloop.car.scanner.ScanProgress
import com.musicloop.car.storage.MusicFolderResolver
import com.musicloop.car.storage.MusicFolderStore
import com.musicloop.car.storage.UsbCoordinator
import com.musicloop.car.storage.UsbMountMonitor
import com.musicloop.car.storage.UsbStorageManager
import com.musicloop.car.ui.folderpicker.FolderPickerActivity
import com.musicloop.car.ui.library.SongListAdapter
import com.musicloop.car.ui.library.SongRow
import com.musicloop.car.ui.state.UsbStatus
import com.musicloop.car.ui.state.UsbUiState
import java.io.File
import java.util.concurrent.Executors

/**
 * Landscape automotive shell. Phase 4 adds native MediaPlayer playback.
 * Playback continues across folder-picker (onStop); the engine is released in onDestroy.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbStorageManager: UsbStorageManager
    private lateinit var coordinator: UsbCoordinator
    private lateinit var mountMonitor: UsbMountMonitor
    private lateinit var scanController: MusicScanController
    private lateinit var trackRepository: RoomTrackRepository
    private lateinit var playback: PlaybackController
    private val songAdapter = SongListAdapter()

    private val usbExecutor = Executors.newSingleThreadExecutor()
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastVolumeIdentity: String? = null
    private var scannedFolderKey: String? = null
    private var seeking: Boolean = false
    private var lastRenderedState: PlayerState? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!::playback.isInitialized) {
                return
            }
            val snapshot = playback.onProgressTick()
            if (!seeking) {
                renderProgress(snapshot)
            }
            if (snapshot.state == PlayerState.PLAYING) {
                mainHandler.postDelayed(this, PlaybackController.PROGRESS_INTERVAL_MS)
            }
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val record = FolderPickerActivity.recordFromResult(result.data)
        if (result.resultCode == RESULT_OK && record != null) {
            coordinator.onFolderSelected(record)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scannedFolderKey = null
            coordinator.refresh()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.songList.adapter = songAdapter

        val app = application as MusicLoopApp
        trackRepository = RoomTrackRepository(app.database.audioTrackDao())
        usbStorageManager = UsbStorageManager(this)
        val store = MusicFolderStore(this)
        coordinator = UsbCoordinator(
            discoverVolumes = { usbStorageManager.discoverMountedVolumes() },
            directoryExists = { path ->
                try {
                    File(path).isDirectory
                } catch (_: Exception) {
                    false
                }
            },
            store = store,
            resolver = MusicFolderResolver { path ->
                try {
                    File(path).isDirectory
                } catch (_: Exception) {
                    false
                }
            },
            ioExecutor = usbExecutor,
            mainPoster = { block -> mainHandler.post(block) },
            listener = { state -> renderUsbState(state) }
        )
        scanController = MusicScanController(
            repository = trackRepository,
            metadataReader = AndroidMetadataReader(),
            ioExecutor = scanExecutor,
            mainPoster = { block -> mainHandler.post(block) },
            listener = { progress -> renderScanProgress(progress) }
        )
        mountMonitor = UsbMountMonitor(this) {
            mainHandler.post { coordinator.refresh() }
        }

        val engine = MediaPlayerEngine(mainHandler)
        val audioFocus = AudioFocusHelper(this) {
            mainHandler.post {
                if (::playback.isInitialized) {
                    playback.pause()
                }
            }
        }
        playback = PlaybackController(
            engine = engine,
            store = PlaybackStateStore(this),
            audioFocus = audioFocus,
            fileAccess = PlaybackPathResolver,
            listener = { snapshot -> renderPlayback(snapshot) }
        )

        bindPlaybackControls()
        playback.restoreDisplayOnly()
        binding.buttonChooseFolder.setOnClickListener { openFolderPicker() }
        applyUsbState(UsbUiState(UsbStatus.WAITING_FOR_USB))
    }

    override fun onStart() {
        super.onStart()
        mountMonitor.start()
        coordinator.start()
        ensureReadPermission()
    }

    override fun onResume() {
        super.onResume()
        coordinator.refresh()
        if (::playback.isInitialized && playback.snapshot().state == PlayerState.PLAYING) {
            scheduleProgress()
        }
    }

    override fun onStop() {
        mountMonitor.stop()
        coordinator.stop()
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(progressRunnable)
        if (::playback.isInitialized) {
            playback.release()
        }
        scanController.cancel()
        usbExecutor.shutdownNow()
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bindPlaybackControls() {
        binding.songList.setOnItemClickListener { _, _, position, _ ->
            val row = songAdapter.getItem(position)
            playback.playUserSelected(row.toQueueItem())
        }
        binding.buttonPlayPause.setOnClickListener { playback.playPause() }
        binding.buttonPrevious.setOnClickListener { playback.previous() }
        binding.buttonNext.setOnClickListener { playback.next() }
        binding.buttonRepeat.setOnClickListener { playback.cycleRepeatMode() }
        binding.buttonShuffle.isEnabled = false
        binding.buttonFavorite.isEnabled = false
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.positionText.text = PlaybackTime.format(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                seeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                seeking = false
                playback.seekTo(seekBar.progress)
            }
        })
    }

    private fun openFolderPicker() {
        if (!hasReadPermission()) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }
        folderPickerLauncher.launch(
            android.content.Intent(this, FolderPickerActivity::class.java)
        )
    }

    private fun ensureReadPermission() {
        if (!hasReadPermission()) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun renderUsbState(state: UsbUiState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyUsbState(state) }
        } else {
            applyUsbState(state)
        }
    }

    private fun applyUsbState(state: UsbUiState) {
        binding.usbStatusValue.setText(statusLabel(state))
        binding.musicFolderValue.text = state.musicFolderLabel ?: getString(R.string.music_folder_placeholder)
        binding.buttonChooseFolder.setText(
            if (state.folderButtonIsChange) R.string.change_music_folder else R.string.choose_music_folder
        )
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
        } else if (state.status == UsbStatus.USB_DISCONNECTED ||
            state.status == UsbStatus.USB_ERROR ||
            state.status == UsbStatus.FOLDER_NOT_FOUND
        ) {
            scanController.cancel()
            scannedFolderKey = null
            if (::playback.isInitialized) {
                playback.onUsbDisconnected()
            }
        } else if (state.status == UsbStatus.WAITING_FOR_USB && !state.usbPresent) {
            scanController.cancel()
            scannedFolderKey = null
            if (::playback.isInitialized && playback.snapshot().track != null) {
                playback.onUsbDisconnected()
            }
        }
        reloadSongs(lastVolumeIdentity)
    }

    private fun renderScanProgress(progress: ScanProgress) {
        binding.scanStatusValue.text = when (progress.phase) {
            ScanPhase.IDLE -> getString(R.string.library_empty)
            ScanPhase.ENUMERATING -> getString(R.string.scan_in_progress)
            ScanPhase.CHECKING -> getString(
                R.string.scan_checking,
                progress.processed,
                progress.total
            )
            ScanPhase.COMPLETE -> {
                val complete = getString(R.string.scan_complete, progress.indexedCount)
                val ready = getString(R.string.scan_ready_count, progress.readyCount)
                val unverified = if (progress.unverifiedCount > 0) {
                    " • " + getString(R.string.scan_unverified_count, progress.unverifiedCount)
                } else {
                    ""
                }
                "$complete • $ready$unverified"
            }
            ScanPhase.INTERRUPTED -> getString(R.string.scan_interrupted)
        }
        if (progress.indexedCount > 0) {
            binding.musicCountValue.text = getString(R.string.music_count_format, progress.indexedCount)
        }
        reloadSongs(lastVolumeIdentity)
    }

    private fun reloadSongs(volumeIdentity: String?) {
        if (volumeIdentity.isNullOrBlank()) {
            songAdapter.submit(emptyList())
            playback.setQueue(emptyList())
            return
        }
        usbExecutor.execute {
            val tracks = try {
                trackRepository.tracksForVolume(volumeIdentity)
            } catch (_: Exception) {
                emptyList()
            }
            val rows = tracks.map { SongRow.from(it) }
            val queue = rows.map { it.toQueueItem() }
            mainHandler.post {
                songAdapter.submit(rows)
                playback.setQueue(queue)
                if (rows.isNotEmpty()) {
                    binding.musicCountValue.text = getString(R.string.music_count_format, rows.size)
                }
            }
        }
    }

    private fun renderPlayback(snapshot: PlaybackSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyPlayback(snapshot) }
        } else {
            applyPlayback(snapshot)
        }
    }

    private fun applyPlayback(snapshot: PlaybackSnapshot) {
        val track = snapshot.track
        if (track == null) {
            binding.songTitle.setText(R.string.now_playing_empty_title)
            binding.songArtist.setText(R.string.now_playing_empty_artist)
            binding.songAlbum.setText(R.string.now_playing_empty_album)
        } else {
            binding.songTitle.text = track.title.ifBlank { track.filename }
            binding.songArtist.text = track.artist.ifBlank { "—" }
            binding.songAlbum.text = track.album.ifBlank { "—" }
        }

        val statusText = statusText(snapshot)
        if (statusText == null) {
            binding.nowPlayingStatus.visibility = View.GONE
        } else {
            binding.nowPlayingStatus.visibility = View.VISIBLE
            binding.nowPlayingStatus.text = statusText
        }

        val hasTrack = track != null
        val usbGone = snapshot.state == PlayerState.USB_DISCONNECTED
        binding.buttonPlayPause.isEnabled = hasTrack && !usbGone && snapshot.state != PlayerState.PREPARING
        binding.buttonPrevious.isEnabled = hasTrack && !usbGone
        binding.buttonNext.isEnabled = hasTrack && !usbGone
        binding.buttonRepeat.isEnabled = true
        binding.buttonRepeat.setText(
            if (snapshot.repeatMode == RepeatMode.ONE) R.string.action_repeat_one else R.string.action_repeat
        )
        binding.buttonPlayPause.setText(
            if (snapshot.state == PlayerState.PLAYING) R.string.action_pause else R.string.action_play_pause
        )

        if (!seeking) {
            renderProgress(snapshot)
        }

        if (snapshot.state == PlayerState.PLAYING) {
            scheduleProgress()
        } else {
            mainHandler.removeCallbacks(progressRunnable)
        }

        maybeToast(snapshot)
    }

    private fun renderProgress(snapshot: PlaybackSnapshot) {
        val duration = snapshot.durationMs.coerceAtLeast(0)
        val position = snapshot.positionMs.coerceAtLeast(0)
        binding.seekBar.max = if (duration > 0) duration else 1000
        binding.seekBar.progress = if (duration > 0) position.coerceAtMost(duration) else 0
        binding.seekBar.isEnabled = snapshot.canSeek
        binding.positionText.text = PlaybackTime.format(position)
        binding.durationText.text = if (duration > 0) {
            PlaybackTime.format(duration)
        } else {
            getString(R.string.playback_duration_placeholder)
        }
    }

    private fun statusText(snapshot: PlaybackSnapshot): String? {
        return when (snapshot.message) {
            PlaybackMessage.NONE -> null
            PlaybackMessage.USB_DISCONNECTED -> getString(R.string.usb_status_disconnected)
            PlaybackMessage.CANNOT_PLAY_FILE -> getString(R.string.playback_error_unplayable)
            PlaybackMessage.NO_PLAYABLE_TRACK -> getString(R.string.playback_error_no_playable)
            PlaybackMessage.FILE_MISSING -> getString(R.string.playback_error_missing)
            PlaybackMessage.PREPARING -> getString(R.string.playback_preparing)
        }
    }

    private fun maybeToast(snapshot: PlaybackSnapshot) {
        if (snapshot.state == lastRenderedState) {
            return
        }
        lastRenderedState = snapshot.state
        val text = when (snapshot.message) {
            PlaybackMessage.CANNOT_PLAY_FILE -> getString(R.string.playback_error_unplayable)
            PlaybackMessage.NO_PLAYABLE_TRACK -> getString(R.string.playback_error_no_playable)
            PlaybackMessage.FILE_MISSING -> getString(R.string.playback_error_missing)
            else -> null
        }
        if (text != null) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleProgress() {
        mainHandler.removeCallbacks(progressRunnable)
        mainHandler.postDelayed(progressRunnable, PlaybackController.PROGRESS_INTERVAL_MS)
    }

    private fun statusLabel(state: UsbUiState): Int {
        return when (state.status) {
            UsbStatus.SCANNING_USB -> R.string.usb_status_scanning
            UsbStatus.USB_READY, UsbStatus.NEEDS_FOLDER -> R.string.usb_status_connected
            UsbStatus.USB_DISCONNECTED -> R.string.usb_status_disconnected
            UsbStatus.USB_ERROR -> R.string.usb_status_error
            UsbStatus.FOLDER_NOT_FOUND -> R.string.usb_status_folder_not_found
            UsbStatus.WAITING_FOR_USB -> {
                if (state.usbPresent) R.string.usb_status_connected else R.string.usb_status_waiting
            }
        }
    }
}
