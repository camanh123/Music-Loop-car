package com.musicloop.car

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.musicloop.car.databinding.ActivityMainBinding
import com.musicloop.car.storage.MusicFolderStore
import com.musicloop.car.storage.UsbCoordinator
import com.musicloop.car.storage.UsbMountMonitor
import com.musicloop.car.storage.UsbStorageManager
import com.musicloop.car.storage.MusicFolderResolver
import com.musicloop.car.ui.folderpicker.FolderPickerActivity
import com.musicloop.car.ui.state.UsbStatus
import com.musicloop.car.ui.state.UsbUiState
import java.io.File
import java.util.concurrent.Executors

/**
 * Landscape automotive shell. Phase 2 wires USB discovery and folder memory only.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbStorageManager: UsbStorageManager
    private lateinit var coordinator: UsbCoordinator
    private lateinit var mountMonitor: UsbMountMonitor

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

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
            coordinator.refresh()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderPlaybackPlaceholder()

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
            ioExecutor = ioExecutor,
            mainPoster = { block -> mainHandler.post(block) },
            listener = { state -> renderUsbState(state) }
        )
        mountMonitor = UsbMountMonitor(this) {
            mainHandler.post { coordinator.refresh() }
        }

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
    }

    override fun onStop() {
        mountMonitor.stop()
        coordinator.stop()
        super.onStop()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
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

    private fun renderPlaybackPlaceholder() {
        binding.musicCountValue.setText(R.string.music_count_placeholder)
        binding.songTitle.setText(R.string.now_playing_empty_title)
        binding.songArtist.setText(R.string.now_playing_empty_artist)
        binding.songAlbum.setText(R.string.now_playing_empty_album)
        binding.positionText.setText(R.string.playback_position_placeholder)
        binding.durationText.setText(R.string.playback_duration_placeholder)
        binding.seekBar.progress = 0
        binding.seekBar.isEnabled = false
        binding.buttonPrevious.isEnabled = false
        binding.buttonPlayPause.isEnabled = false
        binding.buttonNext.isEnabled = false
        binding.buttonShuffle.isEnabled = false
        binding.buttonRepeat.isEnabled = false
        binding.buttonFavorite.isEnabled = false
    }
}
