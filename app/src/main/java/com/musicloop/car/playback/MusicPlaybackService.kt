package com.musicloop.car.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.musicloop.car.MusicLoopApp
import com.musicloop.car.database.RoomTrackRepository
import com.musicloop.car.player.AudioFocusHelper
import com.musicloop.car.player.MediaPlayerEngine
import com.musicloop.car.player.PlaybackController
import com.musicloop.car.player.PlaybackLog
import com.musicloop.car.player.PlaybackPathResolver
import com.musicloop.car.player.PlaybackSnapshot
import com.musicloop.car.player.PlaybackStateStore
import com.musicloop.car.player.PlayerState
import com.musicloop.car.playlist.RoomPlaylistRepository
import com.musicloop.car.scanner.AndroidMetadataReader
import com.musicloop.car.scanner.AudioTrack
import com.musicloop.car.scanner.MusicScanController
import com.musicloop.car.scanner.ScanProgress
import com.musicloop.car.settings.AppSettingsStore
import com.musicloop.car.storage.MusicFolderResolver
import com.musicloop.car.storage.MusicFolderStore
import com.musicloop.car.storage.UsbCoordinator
import com.musicloop.car.storage.UsbMountMonitor
import com.musicloop.car.storage.UsbStorageManager
import com.musicloop.car.ui.state.UsbUiState
import java.io.File
import java.util.concurrent.Executors

/**
 * Authoritative long-lived playback owner. One MediaPlayer via PlaybackController.
 */
class MusicPlaybackService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): MusicPlaybackService = this@MusicPlaybackService
    }

    interface UiCallbacks {
        fun onUsb(state: UsbUiState)
        fun onPlayback(snapshot: PlaybackSnapshot)
        fun onScan(progress: ScanProgress)
        fun onLibrary(tracks: List<AudioTrack>, volumeIdentity: String?)
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val usbExecutor = Executors.newSingleThreadExecutor()
    private val scanExecutor = Executors.newSingleThreadExecutor()

    lateinit var playback: PlaybackController
        private set
    lateinit var session: MusicSession
        private set
    lateinit var trackRepository: RoomTrackRepository
        private set
    lateinit var playlistRepository: RoomPlaylistRepository
        private set
    lateinit var settings: AppSettingsStore
        private set
    lateinit var coordinator: UsbCoordinator
        private set

    private lateinit var mountMonitor: UsbMountMonitor
    private var ui: UiCallbacks? = null
    private var lastNotificationKey: String = ""
    private var created: Boolean = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!created) {
                return
            }
            val snapshot = playback.onProgressTick()
            dispatchPlayback(snapshot)
            if (snapshot.state == PlayerState.PLAYING) {
                mainHandler.postDelayed(this, PlaybackController.PROGRESS_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            PlaybackNotification.ensureChannel(this)
            startForeground(
                PlaybackNotification.NOTIFICATION_ID,
                PlaybackNotification.build(this, PlaybackSnapshot())
            )
            createSession()
            created = true
        } catch (error: Exception) {
            PlaybackLog.e("service onCreate failed", error)
            try {
                startForeground(
                    PlaybackNotification.NOTIFICATION_ID,
                    PlaybackNotification.build(this, PlaybackSnapshot())
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (!created) {
                onCreate()
            }
            startForeground(
                PlaybackNotification.NOTIFICATION_ID,
                PlaybackNotification.build(this, if (created) playback.snapshot() else PlaybackSnapshot())
            )
            if (intent?.getBooleanExtra(EXTRA_BOOT, false) == true && created) {
                session.setBootStart(true)
            }
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> if (created) playback.playPause()
                ACTION_NEXT -> if (created) playback.next()
                ACTION_PREVIOUS -> if (created) playback.previous()
            }
            if (created) {
                mountMonitor.start()
                coordinator.start()
            }
        } catch (error: Exception) {
            PlaybackLog.e("service onStartCommand failed", error)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        created = false
        mainHandler.removeCallbacks(progressRunnable)
        ui = null
        try {
            mountMonitor.stop()
        } catch (_: Exception) {
        }
        try {
            if (::session.isInitialized) {
                session.release()
            }
        } catch (error: Exception) {
            PlaybackLog.e("service release failed", error)
        }
        usbExecutor.shutdownNow()
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    fun setUiCallbacks(callbacks: UiCallbacks?) {
        ui = callbacks
        if (callbacks != null && created) {
            callbacks.onUsb(session.lastUsb)
            callbacks.onPlayback(playback.snapshot())
            callbacks.onLibrary(session.lastTracks, session.lastUsb.volumeIdentity)
        }
    }

    fun refreshUsb() {
        if (created) {
            coordinator.refresh()
        }
    }

    private fun createSession() {
        val app = application as MusicLoopApp
        settings = AppSettingsStore(this)
        trackRepository = RoomTrackRepository(app.database.audioTrackDao())
        playlistRepository = RoomPlaylistRepository(app.database.playlistDao())
        val engine = MediaPlayerEngine(mainHandler)
        val audioFocus = AudioFocusHelper(this) {
            mainHandler.post {
                if (created) {
                    playback.pause()
                }
            }
        }
        playback = PlaybackController(
            engine = engine,
            store = PlaybackStateStore(this),
            audioFocus = audioFocus,
            fileAccess = PlaybackPathResolver,
            listener = { snapshot -> dispatchPlayback(snapshot) }
        )
        val usbStorageManager = UsbStorageManager(this)
        val folderStore = MusicFolderStore(this)
        coordinator = UsbCoordinator(
            discoverVolumes = {
                try {
                    usbStorageManager.discoverMountedVolumes()
                } catch (_: Exception) {
                    emptyList()
                }
            },
            directoryExists = { path ->
                try {
                    File(path).isDirectory
                } catch (_: Exception) {
                    false
                }
            },
            store = folderStore,
            resolver = MusicFolderResolver { path ->
                try {
                    File(path).isDirectory
                } catch (_: Exception) {
                    false
                }
            },
            ioExecutor = usbExecutor,
            mainPoster = { block -> mainHandler.post(block) },
            listener = { state ->
                if (::session.isInitialized) {
                    session.onUsbState(state)
                }
            }
        )
        val scanController = MusicScanController(
            repository = trackRepository,
            metadataReader = AndroidMetadataReader(),
            ioExecutor = scanExecutor,
            mainPoster = { block -> mainHandler.post(block) },
            listener = { progress ->
                if (::session.isInitialized) {
                    session.onScan(progress)
                }
            }
        )
        session = MusicSession(
            playback = playback,
            coordinator = coordinator,
            scanController = scanController,
            tracks = trackRepository,
            settings = settings,
            hasReadPermission = { hasReadPermission() },
            ioExecutor = usbExecutor,
            mainPoster = { block -> mainHandler.post(block) },
            listener = object : MusicSession.Listener {
                override fun onUsb(state: UsbUiState) {
                    ui?.onUsb(state)
                }

                override fun onPlayback(snapshot: PlaybackSnapshot) {
                    ui?.onPlayback(snapshot)
                }

                override fun onScan(progress: ScanProgress) {
                    ui?.onScan(progress)
                }

                override fun onLibrary(tracks: List<AudioTrack>, volumeIdentity: String?) {
                    ui?.onLibrary(tracks, volumeIdentity)
                }
            }
        )
        mountMonitor = UsbMountMonitor(this) {
            mainHandler.post { coordinator.refresh() }
        }
        playback.restoreDisplayOnly()
    }

    private fun dispatchPlayback(snapshot: PlaybackSnapshot) {
        session.onPlayback(snapshot)
        updateNotification(snapshot)
        if (snapshot.state == PlayerState.PLAYING) {
            mainHandler.removeCallbacks(progressRunnable)
            mainHandler.postDelayed(progressRunnable, PlaybackController.PROGRESS_INTERVAL_MS)
        } else {
            mainHandler.removeCallbacks(progressRunnable)
        }
    }

    private fun updateNotification(snapshot: PlaybackSnapshot) {
        val key = "${snapshot.state}|${snapshot.track?.relativePath}|${snapshot.track?.title}"
        if (key == lastNotificationKey) {
            return
        }
        lastNotificationKey = key
        try {
            startForeground(PlaybackNotification.NOTIFICATION_ID, PlaybackNotification.build(this, snapshot))
        } catch (error: Exception) {
            PlaybackLog.w("notification update failed: ${error.message}")
        }
    }

    private fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.musicloop.car.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.musicloop.car.action.NEXT"
        const val ACTION_PREVIOUS = "com.musicloop.car.action.PREVIOUS"
        const val EXTRA_BOOT = "from_boot"

        fun start(context: Context, fromBoot: Boolean = false) {
            val intent = Intent(context, MusicPlaybackService::class.java)
                .putExtra(EXTRA_BOOT, fromBoot)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
