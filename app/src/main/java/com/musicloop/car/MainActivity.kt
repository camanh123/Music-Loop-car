package com.musicloop.car

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.musicloop.car.databinding.ActivityMainBinding
import com.musicloop.car.databinding.ItemMediaBinding
import com.musicloop.car.library.LibraryUiState
import com.musicloop.car.library.MediaListRow
import com.musicloop.car.library.ScanUiState
import com.musicloop.car.storage.CapabilityReportFormatter
import com.musicloop.car.storage.DeviceInfo
import com.musicloop.car.storage.UsbStorageManager
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Phase 2A library / scanner UI plus the Phase 1 USB capability report.
 * Item clicks do not start playback.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaAdapter = MediaListAdapter()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAfterPermission()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            binding.reportText.setText(R.string.permission_required)
        }
    }

    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mediaList.adapter = mediaAdapter
        binding.mediaList.setOnItemClickListener { _, _, _, _ ->
            // Phase 2A: metadata only. Do not play.
        }
        binding.buttonCapability.setOnClickListener {
            withReadPermission { runCapabilityScan() }
        }
        binding.buttonScanLibrary.setOnClickListener {
            withReadPermission { musicLoopApp().lifecycleController.scanOrRescan() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().lifecycleController.uiState.collect { state ->
                    renderLibrary(state)
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
        binding.reportText.setText(R.string.scan_running)
        ioExecutor.execute {
            val report = try {
                val volumes = UsbStorageManager(applicationContext).inspectAllVolumes()
                CapabilityReportFormatter.format(deviceInfo(), volumes)
            } catch (error: Exception) {
                "USB capability scan failed: ${error.javaClass.simpleName}: ${error.message ?: "-"}"
            }
            mainHandler.post {
                binding.reportText.text = report
                binding.buttonCapability.isEnabled = true
            }
        }
    }

    private fun renderLibrary(state: LibraryUiState) {
        binding.usbStatus.text = getString(
            if (state.usbOnline) R.string.usb_online else R.string.usb_offline
        )
        binding.usbStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (state.usbOnline) R.color.status_online else R.color.status_offline
            )
        )
        binding.volumeDescription.text = getString(
            R.string.volume_label,
            state.volumeDescription.ifBlank { getString(R.string.volume_unknown) }
        )
        binding.scanStatus.text = statusLabel(state)
        binding.scanProgress.max = 100
        binding.scanProgress.progress = state.progress.percent
        binding.progressLabel.text = if (state.scanState == ScanUiState.SCANNING) {
            if (state.progress.total > 0) {
                getString(R.string.scan_progress_count, state.progress.scanned, state.progress.total)
            } else {
                getString(R.string.scan_progress_unknown)
            }
        } else {
            getString(R.string.scan_progress_percent, state.progress.percent)
        }
        binding.countsText.text = getString(
            R.string.library_counts,
            state.audioCount,
            state.videoCount,
            state.totalCount
        )
        val scanning = state.scanState == ScanUiState.SCANNING ||
            state.scanState == ScanUiState.DETECTING_USB
        binding.buttonScanLibrary.isEnabled = !scanning
        mediaAdapter.submit(state.media)
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

    private class MediaListAdapter : BaseAdapter() {
        private var rows: List<MediaListRow> = emptyList()

        fun submit(items: List<MediaListRow>) {
            rows = items
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): MediaListRow = rows[position]

        override fun getItemId(position: Int): Long = rows[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = LayoutInflater.from(parent.context)
            val itemBinding = if (convertView != null) {
                ItemMediaBinding.bind(convertView)
            } else {
                ItemMediaBinding.inflate(inflater, parent, false)
            }
            val row = rows[position]
            itemBinding.fileName.text = row.fileName
            itemBinding.metaLine.text = formatMeta(row)
            return itemBinding.root
        }

        private fun formatMeta(row: MediaListRow): String {
            val type = row.mediaType
            val size = formatSize(row.sizeBytes)
            val duration = row.durationMs?.let { formatDuration(it) } ?: "--:--"
            val title = row.title?.takeIf { it.isNotBlank() }
            val artist = row.artist?.takeIf { it.isNotBlank() }
            val tag = when {
                title != null && artist != null -> "$artist — $title"
                title != null -> title
                artist != null -> artist
                else -> ""
            }
            return if (tag.isBlank()) {
                "$type  $size  $duration"
            } else {
                "$type  $size  $duration  $tag"
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes < 1024) {
                return "$bytes B"
            }
            val kb = bytes / 1024.0
            if (kb < 1024) {
                return String.format(Locale.US, "%.1f KB", kb)
            }
            return String.format(Locale.US, "%.1f MB", kb / 1024.0)
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
