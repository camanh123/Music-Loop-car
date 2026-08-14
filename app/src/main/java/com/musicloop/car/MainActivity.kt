package com.musicloop.car

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.musicloop.car.databinding.ActivityMainBinding
import com.musicloop.car.player.PlaybackMessage
import com.musicloop.car.player.PlaybackSnapshot
import com.musicloop.car.player.PlaybackTime
import com.musicloop.car.player.PlayerState
import com.musicloop.car.player.QueueSource
import com.musicloop.car.player.RepeatMode
import com.musicloop.car.playlist.Playlist
import com.musicloop.car.playback.MusicPlaybackService
import com.musicloop.car.scanner.AudioTrack
import com.musicloop.car.scanner.ScanPhase
import com.musicloop.car.scanner.ScanProgress
import com.musicloop.car.ui.folderpicker.FolderPickerActivity
import com.musicloop.car.ui.library.PlaylistListAdapter
import com.musicloop.car.ui.library.SongListAdapter
import com.musicloop.car.ui.library.SongRow
import com.musicloop.car.ui.settings.SettingsActivity
import com.musicloop.car.ui.state.UsbStatus
import com.musicloop.car.ui.state.UsbUiState

/**
 * Landscape automotive shell. Playback lives in MusicPlaybackService.
 * Activity bind/unbind must not start a second MediaPlayer or stop audio.
 */
class MainActivity : AppCompatActivity(), MusicPlaybackService.UiCallbacks {

    private lateinit var binding: ActivityMainBinding
    private val songAdapter = SongListAdapter()
    private val playlistAdapter = PlaylistListAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var service: MusicPlaybackService? = null
    private var bound: Boolean = false
    private var seeking: Boolean = false
    private var lastRenderedState: PlayerState? = null
    private var libraryTab: LibraryTab = LibraryTab.SONGS
    private var allTracks: List<AudioTrack> = emptyList()
    private var openPlaylistId: Long? = null
    private var lastScanProgress: ScanProgress? = null

    private enum class LibraryTab { SONGS, FAVORITES, PLAYLISTS }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? MusicPlaybackService.LocalBinder ?: return
            service = local.service()
            bound = true
            service?.setUiCallbacks(this@MainActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val record = FolderPickerActivity.recordFromResult(result.data)
        if (result.resultCode == RESULT_OK && record != null) {
            service?.coordinator?.onFolderSelected(record)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            service?.refreshUsb()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.songList.adapter = songAdapter
        bindControls()
    }

    override fun onStart() {
        super.onStart()
        MusicPlaybackService.start(this, fromBoot = false)
        bindService(Intent(this, MusicPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
        ensureReadPermission()
    }

    override fun onStop() {
        if (bound) {
            service?.setUiCallbacks(null)
            unbindService(connection)
            bound = false
        }
        super.onStop()
    }

    override fun onUsb(state: UsbUiState) {
        mainHandler.post { applyUsbState(state) }
    }

    override fun onPlayback(snapshot: PlaybackSnapshot) {
        mainHandler.post { applyPlayback(snapshot) }
    }

    override fun onScan(progress: ScanProgress) {
        mainHandler.post {
            lastScanProgress = progress
            renderScanProgress(progress)
        }
    }

    override fun onLibrary(tracks: List<AudioTrack>, volumeIdentity: String?) {
        mainHandler.post {
            allTracks = tracks
            if (tracks.isNotEmpty()) {
                binding.musicCountValue.text = getString(R.string.music_count_format, tracks.size)
            }
            renderLibrary()
        }
    }

    private fun bindControls() {
        binding.buttonChooseFolder.setOnClickListener { openFolderPicker() }
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.buttonPlayPause.setOnClickListener { service?.playback?.playPause() }
        binding.buttonPrevious.setOnClickListener { service?.playback?.previous() }
        binding.buttonNext.setOnClickListener { service?.playback?.next() }
        binding.buttonRepeat.setOnClickListener { service?.playback?.cycleRepeatMode() }
        binding.buttonShuffle.setOnClickListener { service?.playback?.toggleShuffle() }
        binding.buttonFavorite.setOnClickListener { service?.session?.toggleFavorite() }
        binding.tabSongs.setOnClickListener { selectTab(LibraryTab.SONGS) }
        binding.tabFavorites.setOnClickListener { selectTab(LibraryTab.FAVORITES) }
        binding.tabPlaylists.setOnClickListener { selectTab(LibraryTab.PLAYLISTS) }
        binding.buttonLibraryAction.setOnClickListener { onLibraryAction() }
        binding.buttonLibrarySecondary.setOnClickListener { onLibrarySecondary() }
        binding.buttonLibraryTertiary.setOnClickListener { addCurrentToOpenPlaylist() }
        binding.songList.setOnItemClickListener { _, _, position, _ ->
            onLibraryItemClick(position)
        }
        binding.songList.setOnItemLongClickListener { _, _, position, _ ->
            onLibraryItemLongClick(position)
            true
        }
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
                service?.playback?.seekTo(seekBar.progress)
            }
        })
    }

    private fun selectTab(tab: LibraryTab) {
        libraryTab = tab
        if (tab != LibraryTab.PLAYLISTS) {
            openPlaylistId = null
        }
        renderLibrary()
    }

    private fun renderLibrary() {
        when (libraryTab) {
            LibraryTab.SONGS -> {
                binding.playlistActions.visibility = View.GONE
                binding.songList.adapter = songAdapter
                val rows = allTracks.map { SongRow.from(it) }
                songAdapter.submit(rows)
                if (lastScanProgress == null) {
                    binding.scanStatusValue.setText(R.string.library_empty)
                }
            }
            LibraryTab.FAVORITES -> {
                binding.playlistActions.visibility = View.GONE
                binding.songList.adapter = songAdapter
                val rows = allTracks.filter { it.favorite }.map { SongRow.from(it) }
                songAdapter.submit(rows)
                binding.scanStatusValue.setText(
                    if (rows.isEmpty()) R.string.favorites_empty else R.string.library_favorites
                )
            }
            LibraryTab.PLAYLISTS -> renderPlaylists()
        }
    }

    private fun renderPlaylists() {
        val svc = service ?: return
        binding.playlistActions.visibility = View.VISIBLE
        val openId = openPlaylistId
        if (openId == null) {
            binding.songList.adapter = playlistAdapter
            val playlists = try {
                svc.playlistRepository.all()
            } catch (_: Exception) {
                emptyList()
            }
            playlistAdapter.submit(playlists)
            binding.buttonLibraryAction.setText(R.string.playlist_create)
            binding.buttonLibrarySecondary.visibility = View.GONE
            binding.buttonLibraryTertiary.visibility = View.GONE
            binding.scanStatusValue.setText(
                if (playlists.isEmpty()) R.string.playlist_empty else R.string.library_playlists
            )
        } else {
            binding.songList.adapter = songAdapter
            val playlist = try {
                svc.playlistRepository.find(openId)
            } catch (_: Exception) {
                null
            }
            val ids = try {
                svc.playlistRepository.orderedTrackIds(openId)
            } catch (_: Exception) {
                emptyList()
            }
            val rows = try {
                svc.trackRepository.tracksByIdsPreservingOrder(ids).map { SongRow.from(it) }
            } catch (_: Exception) {
                emptyList()
            }
            songAdapter.submit(rows)
            binding.buttonLibraryAction.setText(R.string.playlist_back)
            binding.buttonLibrarySecondary.visibility = View.VISIBLE
            binding.buttonLibrarySecondary.setText(R.string.playlist_play)
            binding.buttonLibraryTertiary.visibility = View.VISIBLE
            binding.scanStatusValue.text = playlist?.name ?: getString(R.string.library_playlists)
            if (rows.isEmpty()) {
                binding.scanStatusValue.setText(R.string.playlist_tracks_empty)
            }
        }
    }

    private fun onLibraryItemClick(position: Int) {
        when (libraryTab) {
            LibraryTab.SONGS -> {
                val row = songAdapter.getItem(position)
                val queue = (0 until songAdapter.count).map { songAdapter.getItem(it).toQueueItem() }
                service?.session?.playFromVisibleList(row.toQueueItem(), queue, QueueSource.ALL_SONGS, null)
            }
            LibraryTab.FAVORITES -> {
                val row = songAdapter.getItem(position)
                val queue = (0 until songAdapter.count).map { songAdapter.getItem(it).toQueueItem() }
                service?.session?.playFromVisibleList(row.toQueueItem(), queue, QueueSource.FAVORITES, null)
            }
            LibraryTab.PLAYLISTS -> {
                if (openPlaylistId == null) {
                    openPlaylistId = playlistAdapter.getItem(position).id
                    renderPlaylists()
                } else {
                    val row = songAdapter.getItem(position)
                    val queue = (0 until songAdapter.count).map { songAdapter.getItem(it).toQueueItem() }
                    service?.session?.playFromVisibleList(
                        row.toQueueItem(),
                        queue,
                        QueueSource.PLAYLIST,
                        openPlaylistId
                    )
                }
            }
        }
    }

    private fun onLibraryItemLongClick(position: Int) {
        when (libraryTab) {
            LibraryTab.SONGS, LibraryTab.FAVORITES -> {
                val row = songAdapter.getItem(position)
                showAddToPlaylistDialog(row.id)
            }
            LibraryTab.PLAYLISTS -> {
                if (openPlaylistId == null) {
                    showPlaylistEditDialog(playlistAdapter.getItem(position))
                } else {
                    val row = songAdapter.getItem(position)
                    val playlistId = openPlaylistId ?: return
                    service?.playlistRepository?.removeTrack(playlistId, row.id)
                    Toast.makeText(this, R.string.playlist_removed, Toast.LENGTH_SHORT).show()
                    renderPlaylists()
                }
            }
        }
    }

    private fun onLibraryAction() {
        if (libraryTab != LibraryTab.PLAYLISTS) {
            return
        }
        if (openPlaylistId == null) {
            showCreatePlaylistDialog()
        } else {
            openPlaylistId = null
            renderPlaylists()
        }
    }

    private fun onLibrarySecondary() {
        val playlistId = openPlaylistId ?: return
        val queue = (0 until songAdapter.count).map { songAdapter.getItem(it).toQueueItem() }
        val first = queue.firstOrNull { it.playable } ?: queue.firstOrNull() ?: return
        service?.session?.playFromVisibleList(first, queue, QueueSource.PLAYLIST, playlistId)
    }

    private fun addCurrentToOpenPlaylist() {
        val playlistId = openPlaylistId ?: return
        val trackId = service?.playback?.snapshot()?.track?.id ?: return
        if (trackId <= 0L) {
            return
        }
        val added = try {
            service?.playlistRepository?.addTrack(playlistId, trackId) == true
        } catch (_: Exception) {
            false
        }
        Toast.makeText(
            this,
            if (added) R.string.playlist_added else R.string.playlist_duplicate,
            Toast.LENGTH_SHORT
        ).show()
        renderPlaylists()
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = getString(R.string.playlist_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_create)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString()
                if (name.isBlank()) {
                    return@setPositiveButton
                }
                try {
                    service?.playlistRepository?.create(name, System.currentTimeMillis())
                } catch (_: Exception) {
                }
                renderPlaylists()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPlaylistEditDialog(playlist: Playlist) {
        val options = arrayOf(
            getString(R.string.playlist_rename),
            getString(R.string.playlist_delete)
        )
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                if (which == 0) {
                    showRenamePlaylistDialog(playlist)
                } else {
                    service?.playlistRepository?.remove(playlist.id)
                    renderPlaylists()
                }
            }
            .show()
    }

    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val input = EditText(this)
        input.setText(playlist.name)
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                try {
                    service?.playlistRepository?.rename(playlist.id, input.text.toString(), System.currentTimeMillis())
                } catch (_: Exception) {
                }
                renderPlaylists()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddToPlaylistDialog(trackId: Long) {
        val playlists = try {
            service?.playlistRepository?.all().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (playlists.isEmpty()) {
            showCreatePlaylistDialog()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_to_playlist)
            .setItems(playlists.map { it.name }.toTypedArray()) { _, which ->
                val added = try {
                    service?.playlistRepository?.addTrack(playlists[which].id, trackId) == true
                } catch (_: Exception) {
                    false
                }
                Toast.makeText(
                    this,
                    if (added) R.string.playlist_added else R.string.playlist_duplicate,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun openFolderPicker() {
        if (!hasReadPermission()) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }
        folderPickerLauncher.launch(Intent(this, FolderPickerActivity::class.java))
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

    private fun applyUsbState(state: UsbUiState) {
        binding.usbStatusValue.setText(statusLabel(state))
        binding.musicFolderValue.text = state.musicFolderLabel ?: getString(R.string.music_folder_placeholder)
        binding.buttonChooseFolder.setText(
            if (state.folderButtonIsChange) R.string.change_music_folder else R.string.choose_music_folder
        )
    }

    private fun renderScanProgress(progress: ScanProgress) {
        if (libraryTab != LibraryTab.SONGS) {
            return
        }
        binding.scanStatusValue.text = when (progress.phase) {
            ScanPhase.IDLE -> getString(R.string.library_empty)
            ScanPhase.ENUMERATING -> getString(R.string.scan_in_progress)
            ScanPhase.CHECKING -> getString(R.string.scan_checking, progress.processed, progress.total)
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
        binding.buttonShuffle.isEnabled = true
        binding.buttonFavorite.isEnabled = hasTrack
        binding.buttonRepeat.setText(
            when (snapshot.repeatMode) {
                RepeatMode.OFF -> R.string.action_repeat
                RepeatMode.ALL -> R.string.action_repeat_all
                RepeatMode.ONE -> R.string.action_repeat_one
            }
        )
        binding.buttonShuffle.setText(
            if (snapshot.shuffleEnabled) R.string.action_shuffle_on else R.string.action_shuffle
        )
        binding.buttonFavorite.setText(
            if (snapshot.favorite) R.string.action_favorited else R.string.action_favorite
        )
        binding.buttonPlayPause.setText(
            if (snapshot.state == PlayerState.PLAYING) R.string.action_pause else R.string.action_play_pause
        )
        if (!seeking) {
            renderProgress(snapshot)
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
