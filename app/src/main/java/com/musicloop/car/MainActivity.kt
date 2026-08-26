package com.musicloop.car

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicloop.car.databinding.ActivityMainBinding
import com.musicloop.car.databinding.ItemMediaBinding
import com.musicloop.car.library.CollectionRows
import com.musicloop.car.library.LibraryListQuery
import com.musicloop.car.library.LibrarySort
import com.musicloop.car.library.LibraryTab
import com.musicloop.car.library.LibraryUiState
import com.musicloop.car.library.LibraryUiStore
import com.musicloop.car.library.MediaListRow
import com.musicloop.car.library.ScanUiState
import com.musicloop.car.playback.PlayStatus
import com.musicloop.car.playback.PlaybackUiState
import com.musicloop.car.playback.VideoPlaybackStore
import com.musicloop.car.playback.VideoRestore
import com.musicloop.car.storage.CapabilityReportFormatter
import com.musicloop.car.storage.DeviceInfo
import com.musicloop.car.storage.UsbStorageManager
import com.musicloop.car.usb.UsbHostState
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Phase 2D.2 car library UI: favorites, playlists, session queue.
 * Audio plays in-place. Video opens PlayerView. USB stays read-only.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val videoStore by lazy { VideoPlaybackStore(applicationContext) }
    private val libraryStore by lazy { LibraryUiStore(applicationContext) }
    private val mediaAdapter = MediaListAdapter(
        onClick = { row -> onRowClicked(row) },
        onFavorite = { row -> toggleFavorite(row) },
        onMore = { row -> showRowActions(row) }
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAfterPermission()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private var pendingAction: (() -> Unit)? = null
    private var userSeeking = false
    private var libraryTab = LibraryTab.MUSIC
    private var musicSort = LibrarySort.A_Z
    private var videoSort = LibrarySort.A_Z
    private var searchQuery = ""
    private var restoringQuery = false
    private var allMedia: List<MediaListRow> = emptyList()
    private var pendingVideoScrollRestore = true
    private var usbWasOffline = true
    private var lastPlayback = PlaybackUiState()
    private var usbOnline = false
    private var favoriteEntities = emptyList<com.musicloop.car.database.FavoriteEntity>()
    private var favoriteKeys = emptySet<Pair<String, String>>()
    private var playlistRecords = emptyList<com.musicloop.car.database.PlaylistRecord>()
    private var playlistItemEntities = emptyList<com.musicloop.car.database.PlaylistItemEntity>()
    private var openPlaylistId: Long? = null

    private val videoScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                persistVideoScroll()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mediaList.layoutManager = LinearLayoutManager(this)
        binding.mediaList.setHasFixedSize(true)
        binding.mediaList.itemAnimator = null
        binding.mediaList.adapter = mediaAdapter
        binding.mediaList.addOnScrollListener(videoScrollListener)
        binding.tabMusic.setOnClickListener { selectTab(LibraryTab.MUSIC) }
        binding.tabVideo.setOnClickListener { selectTab(LibraryTab.VIDEO) }
        binding.tabFavorites.setOnClickListener { selectTab(LibraryTab.FAVORITES) }
        binding.tabPlaylists.setOnClickListener { selectTab(LibraryTab.PLAYLISTS) }
        binding.buttonSort.setOnClickListener { showSortPicker() }
        binding.buttonQueue.setOnClickListener { showQueue() }
        binding.buttonNewPlaylist.setOnClickListener { promptNewPlaylist() }
        binding.buttonCapability.setOnClickListener {
            withReadPermission { runCapabilityScan() }
        }
        binding.buttonScanLibrary.setOnClickListener {
            withReadPermission { musicLoopApp().lifecycleController.manualRescan() }
        }
        binding.buttonPlayPause.setOnClickListener { musicLoopApp().playerManager.playPause() }
        binding.buttonPrevious.setOnClickListener { musicLoopApp().playerManager.previous() }
        binding.buttonNext.setOnClickListener { musicLoopApp().playerManager.next() }
        binding.audioSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                userSeeking = false
                val duration = musicLoopApp().playerManager.state.value.durationMs
                val position = if (duration <= 0L) 0L else (duration * (seekBar?.progress ?: 0)) / 1000L
                musicLoopApp().playerManager.seekTo(position)
            }
        })
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                if (!restoringQuery) {
                    libraryStore.saveQuery(searchQuery)
                }
                showFiltered()
            }
        })
        val saved = libraryStore.load()
        musicSort = saved.musicSort
        videoSort = saved.videoSort
        if (saved.query.isNotEmpty()) {
            restoringQuery = true
            binding.searchInput.setText(saved.query)
            restoringQuery = false
        }
        selectTab(saved.tab)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().lifecycleController.setForegroundPolling(true)
                try {
                    musicLoopApp().lifecycleController.uiState.collect { state ->
                        renderLibrary(state)
                    }
                } finally {
                    musicLoopApp().lifecycleController.setForegroundPolling(false)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().playerManager.state.collect { state ->
                    renderPlayback(state)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().collections.observeFavorites().collect { rows ->
                    favoriteEntities = rows
                    favoriteKeys = rows.map { it.volumeId to it.relativePath }.toSet()
                    mediaAdapter.setFavorites(favoriteKeys)
                    if (libraryTab == LibraryTab.FAVORITES) {
                        showFiltered()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().collections.observePlaylists().collect { rows ->
                    playlistRecords = rows
                    if (libraryTab == LibraryTab.PLAYLISTS && openPlaylistId == null) {
                        showFiltered()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().playerManager.coordinator.explicitQueue.collect {
                    binding.buttonQueue.text = if (it.isEmpty()) {
                        getString(R.string.queue)
                    } else {
                        getString(R.string.queue) + " (${it.size})"
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        musicLoopApp().playerManager.reconcileUiFromPlayer()
        if (libraryTab == LibraryTab.VIDEO) {
            pendingVideoScrollRestore = true
            restoreVideoScrollIfNeeded()
        }
    }

    override fun onPause() {
        persistVideoScroll()
        libraryStore.saveTab(libraryTab)
        libraryStore.saveSort(LibraryTab.MUSIC, musicSort)
        libraryStore.saveSort(LibraryTab.VIDEO, videoSort)
        libraryStore.saveQuery(searchQuery)
        super.onPause()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun withReadPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun pendingAfterPermission() {
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }

    private fun runCapabilityScan() {
        binding.buttonCapability.isEnabled = false
        ioExecutor.execute {
            val report = try {
                val volumes = UsbStorageManager(applicationContext).inspectAllVolumes()
                CapabilityReportFormatter.format(deviceInfo(), volumes)
            } catch (error: Exception) {
                "USB capability scan failed: ${error.javaClass.simpleName}: ${error.message ?: "-"}"
            }
            mainHandler.post {
                binding.buttonCapability.isEnabled = true
                showCapabilityReport(report)
            }
        }
    }

    private fun showCapabilityReport(report: String) {
        val scroll = ScrollView(this)
        val text = TextView(this).apply {
            this.text = report
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(40, 24, 40, 24)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }
        scroll.addView(text)
        AlertDialog.Builder(this)
            .setTitle(R.string.scan_usb_capability)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun selectTab(tab: LibraryTab) {
        if (tab != LibraryTab.PLAYLISTS) {
            openPlaylistId = null
        } else if (libraryTab != LibraryTab.PLAYLISTS) {
            openPlaylistId = null
        }
        libraryTab = tab
        libraryStore.saveTab(tab)
        val selected = ContextCompat.getDrawable(this, R.drawable.bg_tab_selected)
        val idle = ContextCompat.getDrawable(this, R.drawable.bg_button)
        binding.tabMusic.background = if (tab == LibraryTab.MUSIC) selected else idle
        binding.tabVideo.background = if (tab == LibraryTab.VIDEO) selected else idle
        binding.tabFavorites.background = if (tab == LibraryTab.FAVORITES) selected else idle
        binding.tabPlaylists.background = if (tab == LibraryTab.PLAYLISTS) selected else idle
        binding.searchInput.hint = when (tab) {
            LibraryTab.VIDEO -> getString(R.string.search_hint_video)
            LibraryTab.PLAYLISTS -> getString(R.string.playlist_name_hint)
            else -> getString(R.string.search_hint)
        }
        binding.buttonSort.visibility = if (tab == LibraryTab.PLAYLISTS) View.GONE else View.VISIBLE
        binding.buttonNewPlaylist.visibility = if (tab == LibraryTab.PLAYLISTS) View.VISIBLE else View.GONE
        updateSortLabel()
        if (tab == LibraryTab.VIDEO) {
            pendingVideoScrollRestore = true
        }
        showFiltered()
    }

    private fun currentSort(): LibrarySort = if (libraryTab == LibraryTab.VIDEO) videoSort else musicSort

    private fun updateSortLabel() {
        binding.buttonSort.text = getString(R.string.sort_label, sortLabel(currentSort()))
    }

    private fun sortLabel(sort: LibrarySort): String {
        return when (sort) {
            LibrarySort.A_Z -> getString(R.string.sort_az)
            LibrarySort.Z_A -> getString(R.string.sort_za)
            LibrarySort.NEWEST -> getString(R.string.sort_newest)
            LibrarySort.OLDEST -> getString(R.string.sort_oldest)
            LibrarySort.ARTIST -> getString(R.string.sort_artist)
            LibrarySort.ALBUM -> getString(R.string.sort_album)
        }
    }

    private fun showSortPicker() {
        val options = LibrarySort.entries.toTypedArray()
        val labels = options.map { sortLabel(it) }.toTypedArray()
        val checked = options.indexOf(currentSort()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = options[which]
                if (libraryTab == LibraryTab.MUSIC) {
                    musicSort = selected
                } else {
                    videoSort = selected
                }
                libraryStore.saveSort(libraryTab, selected)
                updateSortLabel()
                showFiltered()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderLibrary(state: LibraryUiState) {
        val hostLabel = hostLabel(state.usbHostState)
        binding.usbStatus.text = hostLabel
        binding.usbStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (state.usbOnline) R.color.status_online else R.color.status_offline
            )
        )
        val scanning = state.scanState == ScanUiState.SCANNING ||
            state.scanState == ScanUiState.DETECTING_USB
        binding.scanProgress.visibility = if (scanning) View.VISIBLE else View.GONE
        binding.scanProgress.max = 100
        binding.scanProgress.progress = state.progress.percent
        binding.scanStatus.text = getString(
            R.string.library_status_line,
            statusLabel(state),
            state.audioCount,
            state.videoCount
        )
        presentUsbDiagnostic(state)
        if (!state.usbOnline) {
            usbWasOffline = true
        } else if (usbWasOffline) {
            pendingVideoScrollRestore = true
            usbWasOffline = false
        }
        usbOnline = state.usbOnline
        allMedia = state.media
        showFiltered()
    }

    private fun presentUsbDiagnostic(state: LibraryUiState) {
        if (state.usbOnline) {
            val diagnostic = state.diagnosticMessage
            if (diagnostic.isNullOrBlank()) {
                binding.diagnosticText.visibility = View.GONE
                binding.diagnosticText.text = ""
            } else {
                binding.diagnosticText.visibility = View.VISIBLE
                binding.diagnosticText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.diagnosticText.text = diagnostic
            }
            return
        }
        binding.diagnosticText.visibility = View.VISIBLE
        binding.diagnosticText.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
        val diagnostic = state.diagnosticMessage?.takeIf { it.isNotBlank() }
        binding.diagnosticText.text = when {
            !diagnostic.isNullOrBlank() -> diagnostic
            state.usbHostState == UsbHostState.USB_NOT_DETECTED ->
                getString(R.string.usb_not_detected_user)
            else -> getString(R.string.usb_not_detected_user)
        }
    }

    private fun hostLabel(state: UsbHostState): String {
        return when (state) {
            UsbHostState.USB_ONLINE -> getString(R.string.usb_online)
            UsbHostState.USB_READY -> getString(R.string.usb_ready)
            UsbHostState.USB_SCANNING -> getString(R.string.usb_scanning)
            UsbHostState.USB_OFFLINE -> getString(R.string.usb_offline)
            UsbHostState.USB_NOT_DETECTED -> getString(R.string.usb_not_detected)
            UsbHostState.USB_ERROR -> getString(R.string.usb_error)
        }
    }

    private fun showFiltered() {
        val filtered = when (libraryTab) {
            LibraryTab.MUSIC, LibraryTab.VIDEO ->
                LibraryListQuery.apply(allMedia, libraryTab, searchQuery, currentSort())
            LibraryTab.FAVORITES -> CollectionRows.fromFavorites(
                favorites = favoriteEntities,
                library = allMedia,
                usbOnline = usbOnline,
                query = searchQuery,
                sort = musicSort
            )
            LibraryTab.PLAYLISTS -> {
                val playlistId = openPlaylistId
                if (playlistId == null) {
                    CollectionRows.playlistSummaries(playlistRecords, searchQuery)
                } else {
                    CollectionRows.fromPlaylistItems(
                        items = playlistItemEntities,
                        library = allMedia,
                        usbOnline = usbOnline,
                        query = searchQuery
                    )
                }
            }
        }
        mediaAdapter.submit(filtered)
        mediaAdapter.setFavorites(favoriteKeys)
        mediaAdapter.setCurrent(lastPlayback.current?.volumeId, lastPlayback.current?.relativePath)
        binding.emptyHint.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyHint.setText(
            when {
                searchQuery.isNotBlank() -> R.string.empty_search
                libraryTab == LibraryTab.MUSIC -> R.string.empty_music
                libraryTab == LibraryTab.VIDEO -> R.string.empty_video
                libraryTab == LibraryTab.FAVORITES -> R.string.empty_favorites
                else -> R.string.empty_playlists
            }
        )
        if (libraryTab == LibraryTab.VIDEO && searchQuery.isBlank()) {
            restoreVideoScrollIfNeeded()
        }
    }

    private fun persistVideoScroll() {
        if (libraryTab != LibraryTab.VIDEO || mediaAdapter.itemCount <= 0 || searchQuery.isNotBlank()) {
            return
        }
        val layoutManager = binding.mediaList.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val offset = layoutManager.findViewByPosition(position)?.top ?: 0
        videoStore.saveListScroll(position, offset, System.currentTimeMillis())
    }

    private fun restoreVideoScrollIfNeeded() {
        if (libraryTab != LibraryTab.VIDEO || !pendingVideoScrollRestore || searchQuery.isNotBlank()) {
            return
        }
        if (mediaAdapter.itemCount <= 0) {
            return
        }
        val saved = videoStore.load()
        val position = VideoRestore.clampScroll(saved.listPosition, mediaAdapter.itemCount)
        binding.mediaList.post {
            if (libraryTab != LibraryTab.VIDEO || mediaAdapter.itemCount <= 0 || searchQuery.isNotBlank()) {
                return@post
            }
            val layoutManager = binding.mediaList.layoutManager as? LinearLayoutManager ?: return@post
            layoutManager.scrollToPositionWithOffset(position, saved.listOffset)
            pendingVideoScrollRestore = false
        }
    }

    private fun renderPlayback(state: PlaybackUiState) {
        lastPlayback = state
        binding.nowPlayingTitle.text = state.current?.displayTitle ?: getString(R.string.player_idle)
        binding.nowPlayingArtist.text = state.current?.displayArtist.orEmpty()
        binding.positionText.text = formatClock(state.positionMs)
        binding.durationText.text = formatClock(state.durationMs)
        binding.buttonPlayPause.text = if (state.status == PlayStatus.PLAYING) {
            getString(R.string.pause)
        } else {
            getString(R.string.play)
        }
        if (!userSeeking && state.durationMs > 0L) {
            binding.audioSeekBar.progress =
                ((state.positionMs * 1000L) / state.durationMs).toInt().coerceIn(0, 1000)
        }
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            binding.nowPlayingArtist.text = getString(R.string.playback_error, message)
        }
        mediaAdapter.setCurrent(state.current?.volumeId, state.current?.relativePath)
    }

    private fun onRowClicked(row: MediaListRow) {
        when {
            row.mediaType == "VIDEO" -> {
                persistVideoScroll()
                startActivity(VideoActivity.intent(this, row))
            }
            row.mediaType == "PLAYLIST" -> openPlaylist(row.id)
            !row.available -> Toast.makeText(this, R.string.unavailable, Toast.LENGTH_SHORT).show()
            else -> musicLoopApp().playerManager.playItem(row)
        }
    }

    private fun toggleFavorite(row: MediaListRow) {
        if (row.mediaType != "AUDIO") {
            return
        }
        lifecycleScope.launch {
            val collections = musicLoopApp().collections
            val on = collections.isFavorite(row.volumeId, row.relativePath)
            collections.setFavorite(row, !on)
        }
    }

    private fun showRowActions(row: MediaListRow) {
        if (row.mediaType == "PLAYLIST") {
            showPlaylistActions(row)
            return
        }
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        if (row.available && row.mediaType == "AUDIO") {
            labels += getString(R.string.play_next)
            actions += { musicLoopApp().playerManager.playNext(row) }
            labels += getString(R.string.add_to_queue)
            actions += { musicLoopApp().playerManager.enqueue(row) }
        }
        if (row.mediaType == "AUDIO") {
            labels += getString(R.string.add_to_playlist)
            actions += { promptAddToPlaylist(row) }
        }
        if (libraryTab == LibraryTab.PLAYLISTS && openPlaylistId != null) {
            labels += getString(R.string.playlist_remove_item)
            actions += { removePlaylistItem(row) }
        }
        if (labels.isEmpty()) {
            return
        }
        AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun showPlaylistActions(row: MediaListRow) {
        val labels = arrayOf(
            getString(R.string.playlist_play),
            getString(R.string.playlist_rename),
            getString(R.string.playlist_remove)
        )
        AlertDialog.Builder(this)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> playPlaylist(row.id)
                    1 -> promptRenamePlaylist(row)
                    2 -> removePlaylist(row.id)
                }
            }
            .show()
    }

    private fun openPlaylist(id: Long) {
        openPlaylistId = id
        lifecycleScope.launch {
            playlistItemEntities = musicLoopApp().collections.playlistItems(id)
            showFiltered()
        }
    }

    private fun promptNewPlaylist() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.playlist_name_hint)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_new)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    musicLoopApp().collections.createPlaylist(input.text.toString())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptRenamePlaylist(row: MediaListRow) {
        val input = android.widget.EditText(this).apply {
            setText(row.title ?: row.fileName)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    musicLoopApp().collections.renamePlaylist(row.id, input.text.toString())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun removePlaylist(id: Long) {
        lifecycleScope.launch {
            musicLoopApp().collections.removePlaylist(id)
            if (openPlaylistId == id) {
                openPlaylistId = null
                showFiltered()
            }
        }
    }

    private fun promptAddToPlaylist(row: MediaListRow) {
        lifecycleScope.launch {
            val lists = musicLoopApp().collections.playlists()
            if (lists.isEmpty()) {
                val created = musicLoopApp().collections.createPlaylist("Playlist")
                musicLoopApp().collections.addToPlaylist(created.id, row)
                return@launch
            }
            val names = lists.map { it.name }.toTypedArray()
            mainHandler.post {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.add_to_playlist)
                    .setItems(names) { _, which ->
                        lifecycleScope.launch {
                            musicLoopApp().collections.addToPlaylist(lists[which].id, row)
                        }
                    }
                    .setNeutralButton(R.string.playlist_new) { _, _ ->
                        promptNewPlaylistThenAdd(row)
                    }
                    .show()
            }
        }
    }

    private fun promptNewPlaylistThenAdd(row: MediaListRow) {
        val input = android.widget.EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_new)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val created = musicLoopApp().collections.createPlaylist(input.text.toString())
                    musicLoopApp().collections.addToPlaylist(created.id, row)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun removePlaylistItem(row: MediaListRow) {
        val playlistId = openPlaylistId ?: return
        lifecycleScope.launch {
            musicLoopApp().collections.removeFromPlaylist(playlistId, row.volumeId, row.relativePath)
            playlistItemEntities = musicLoopApp().collections.playlistItems(playlistId)
            showFiltered()
        }
    }

    private fun playPlaylist(id: Long) {
        lifecycleScope.launch {
            val items = CollectionRows.fromPlaylistItems(
                items = musicLoopApp().collections.playlistItems(id),
                library = allMedia,
                usbOnline = usbOnline,
                query = ""
            ).filter { it.available && it.mediaType == "AUDIO" }
            if (items.isEmpty()) {
                mainHandler.post {
                    Toast.makeText(this@MainActivity, R.string.unavailable, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            musicLoopApp().playerManager.playItems(items, 0)
        }
    }

    private fun showQueue() {
        val queued = musicLoopApp().playerManager.coordinator.explicitQueue.value
        if (queued.isEmpty()) {
            Toast.makeText(this, R.string.queue_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = queued.map { it.displayTitle }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.queue)
            .setItems(labels) { _, which ->
                AlertDialog.Builder(this)
                    .setItems(arrayOf(getString(R.string.playlist_remove_item))) { _, _ ->
                        musicLoopApp().playerManager.removeQueued(which)
                    }
                    .show()
            }
            .setNeutralButton(R.string.queue_clear) { _, _ ->
                musicLoopApp().playerManager.clearExplicitQueue()
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun formatClock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%d:%02d", total / 60L, total % 60L)
    }

    private fun statusLabel(state: LibraryUiState): String {
        return when (state.scanState) {
            ScanUiState.IDLE -> getString(R.string.state_idle)
            ScanUiState.DETECTING_USB -> getString(R.string.state_detecting)
            ScanUiState.SCANNING -> state.statusMessage.ifBlank { getString(R.string.state_scanning) }
            ScanUiState.COMPLETED -> getString(R.string.state_completed)
            ScanUiState.FAILED -> getString(R.string.state_failed)
            ScanUiState.USB_OFFLINE -> getString(R.string.state_usb_offline)
        }
    }

    private fun musicLoopApp(): MusicLoopApp = application as MusicLoopApp

    private fun deviceInfo(): DeviceInfo {
        val hardware = listOf(Build.HARDWARE, Build.BOARD)
            .mapNotNull { it?.takeIf { value -> value.isNotBlank() } }
            .distinct()
            .joinToString(" / ")
            .ifBlank { "unknown" }
        return DeviceInfo(
            brand = Build.BRAND ?: "unknown",
            model = Build.MODEL ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            hardware = hardware
        )
    }

    private class MediaListAdapter(
        private val onClick: (MediaListRow) -> Unit,
        private val onFavorite: (MediaListRow) -> Unit,
        private val onMore: (MediaListRow) -> Unit
    ) : RecyclerView.Adapter<MediaListAdapter.Holder>() {
        private var rows: List<MediaListRow> = emptyList()
        private var currentVolumeId: String? = null
        private var currentRelativePath: String? = null
        private var favoriteKeys: Set<Pair<String, String>> = emptySet()

        init {
            setHasStableIds(true)
        }

        fun submit(items: List<MediaListRow>) {
            if (items == rows) {
                return
            }
            rows = items
            notifyDataSetChanged()
        }

        fun setFavorites(keys: Set<Pair<String, String>>) {
            if (keys == favoriteKeys) {
                return
            }
            favoriteKeys = keys
            notifyDataSetChanged()
        }

        fun setCurrent(volumeId: String?, relativePath: String?) {
            if (volumeId == currentVolumeId && relativePath == currentRelativePath) {
                return
            }
            val previous = currentIndex()
            currentVolumeId = volumeId
            currentRelativePath = relativePath
            val next = currentIndex()
            if (previous == next) {
                if (next >= 0) {
                    notifyItemChanged(next)
                }
                return
            }
            if (previous >= 0) {
                notifyItemChanged(previous)
            }
            if (next >= 0) {
                notifyItemChanged(next)
            }
        }

        private fun currentIndex(): Int {
            return rows.indexOfFirst { LibraryListQuery.isCurrent(it, currentVolumeId, currentRelativePath) }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding, onClick, onFavorite, onMore)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.bind(
                row = row,
                current = LibraryListQuery.isCurrent(row, currentVolumeId, currentRelativePath),
                favorite = favoriteKeys.contains(row.volumeId to row.relativePath)
            )
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemId(position: Int): Long = rows[position].id

        class Holder(
            private val binding: ItemMediaBinding,
            private val onClick: (MediaListRow) -> Unit,
            private val onFavorite: (MediaListRow) -> Unit,
            private val onMore: (MediaListRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: MediaListRow, current: Boolean, favorite: Boolean) {
                val isVideo = row.mediaType == "VIDEO"
                val isPlaylist = row.mediaType == "PLAYLIST"
                binding.typeGlyph.text = when {
                    isPlaylist -> "☰"
                    isVideo -> binding.root.context.getString(R.string.video_glyph)
                    else -> binding.root.context.getString(R.string.music_glyph)
                }
                binding.titleText.text = row.title?.takeIf { it.isNotBlank() } ?: row.fileName
                binding.subtitleText.text = subtitle(row)
                binding.playGlyph.visibility = if (current) View.VISIBLE else View.GONE
                binding.root.setBackgroundResource(
                    if (current) R.drawable.bg_media_row_current else R.drawable.bg_media_row
                )
                val showFavorite = row.mediaType == "AUDIO"
                binding.buttonFavorite.visibility = if (showFavorite) View.VISIBLE else View.GONE
                if (showFavorite) {
                    binding.buttonFavorite.text = binding.root.context.getString(
                        if (favorite) R.string.favorite_on else R.string.favorite_off
                    )
                    binding.buttonFavorite.setTextColor(
                        ContextCompat.getColor(
                            binding.root.context,
                            if (favorite) R.color.accent else R.color.text_muted
                        )
                    )
                    binding.buttonFavorite.setOnClickListener { onFavorite(row) }
                }
                binding.buttonMore.visibility = if (isVideo) View.GONE else View.VISIBLE
                binding.buttonMore.setOnClickListener { onMore(row) }
                binding.root.setOnClickListener { onClick(row) }
            }

            private fun subtitle(row: MediaListRow): String {
                if (!row.available) {
                    return binding.root.context.getString(R.string.unavailable)
                }
                if (row.mediaType == "PLAYLIST") {
                    return row.artist?.let { "$it songs" } ?: row.fileName
                }
                val artist = row.artist?.takeIf { it.isNotBlank() }
                val album = row.album?.takeIf { it.isNotBlank() }
                return when {
                    artist != null && album != null -> "$artist / $album"
                    artist != null -> artist
                    album != null -> album
                    else -> row.fileName
                }
            }
        }
    }
}
