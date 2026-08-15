package com.musicloop.car

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.musicloop.car.library.LibraryUiState
import com.musicloop.car.library.MediaListRow
import com.musicloop.car.library.ScanUiState
import com.musicloop.car.playback.PlayStatus
import com.musicloop.car.playback.PlaybackUiState
import com.musicloop.car.storage.CapabilityReportFormatter
import com.musicloop.car.storage.DeviceInfo
import com.musicloop.car.storage.UsbStorageManager
import com.musicloop.car.usb.UsbHostState
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Phase 2B.2 car library UI: MUSIC/VIDEO tabs, USB recovery, Media3 playback.
 * Audio plays in-place. Video opens PlayerView. USB stays read-only.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaAdapter = MediaListAdapter { row ->
        if (row.mediaType == "VIDEO") {
            startActivity(VideoActivity.intent(this, row))
        } else {
            musicLoopApp().playerManager.playItem(row)
        }
    }

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
    private var allMedia: List<MediaListRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mediaList.layoutManager = LinearLayoutManager(this)
        binding.mediaList.setHasFixedSize(true)
        binding.mediaList.adapter = mediaAdapter
        binding.tabMusic.setOnClickListener { selectTab(LibraryTab.MUSIC) }
        binding.tabVideo.setOnClickListener { selectTab(LibraryTab.VIDEO) }
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
        selectTab(LibraryTab.MUSIC)
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
        libraryTab = tab
        val selected = ContextCompat.getDrawable(this, R.drawable.bg_tab_selected)
        val idle = ContextCompat.getDrawable(this, R.drawable.bg_button)
        binding.tabMusic.background = if (tab == LibraryTab.MUSIC) selected else idle
        binding.tabVideo.background = if (tab == LibraryTab.VIDEO) selected else idle
        showFiltered()
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
        val diagnostic = state.diagnosticMessage
        if (diagnostic.isNullOrBlank()) {
            binding.diagnosticText.visibility = View.GONE
            binding.diagnosticText.text = ""
        } else {
            binding.diagnosticText.visibility = View.VISIBLE
            binding.diagnosticText.text = diagnostic
        }
        allMedia = state.media
        showFiltered()
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
        val filtered = allMedia.filter { row ->
            if (libraryTab == LibraryTab.MUSIC) row.mediaType == "AUDIO" else row.mediaType == "VIDEO"
        }
        mediaAdapter.submit(filtered)
        binding.emptyHint.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyHint.setText(
            if (libraryTab == LibraryTab.MUSIC) R.string.empty_music else R.string.empty_video
        )
    }

    private fun renderPlayback(state: PlaybackUiState) {
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

    private enum class LibraryTab { MUSIC, VIDEO }

    private class MediaListAdapter(
        private val onClick: (MediaListRow) -> Unit
    ) : RecyclerView.Adapter<MediaListAdapter.Holder>() {
        private var rows: List<MediaListRow> = emptyList()

        fun submit(items: List<MediaListRow>) {
            if (items == rows) {
                return
            }
            rows = items
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding, onClick)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(rows[position])
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemId(position: Int): Long = rows[position].id

        class Holder(
            private val binding: ItemMediaBinding,
            private val onClick: (MediaListRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: MediaListRow) {
                val isVideo = row.mediaType == "VIDEO"
                binding.typeGlyph.text = if (isVideo) {
                    binding.root.context.getString(R.string.video_glyph)
                } else {
                    binding.root.context.getString(R.string.music_glyph)
                }
                binding.titleText.text = row.title?.takeIf { it.isNotBlank() } ?: row.fileName
                binding.subtitleText.text = subtitle(row)
                binding.root.setOnClickListener { onClick(row) }
            }

            private fun subtitle(row: MediaListRow): String {
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
