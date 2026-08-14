package com.musicloop.car.ui.folderpicker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.musicloop.car.R
import com.musicloop.car.databinding.ActivityFolderPickerBinding
import com.musicloop.car.storage.MusicFolderPaths
import com.musicloop.car.storage.MusicFolderRecord
import com.musicloop.car.storage.SafTreeFallback
import com.musicloop.car.storage.UsbStorageManager
import com.musicloop.car.storage.UsbVolume
import java.io.File

/**
 * In-app USB folder browser. Primary picker for CARFU (DocumentsUI is fallback only).
 * Lists directories; never writes to USB.
 */
class FolderPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderPickerBinding
    private lateinit var storageManager: UsbStorageManager

    private var volumes: List<UsbVolume> = emptyList()
    private var currentVolume: UsbVolume? = null
    private var currentDir: File? = null
    private var showingVolumeList = false

    private val safLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        val uri = result.data?.data ?: return@registerForActivityResult
        val path = SafTreeFallback.resolveTreeToFilesystemPath(this, uri)
        if (path == null) {
            Toast.makeText(this, R.string.saf_unavailable, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        finishWithSelection(File(path))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storageManager = UsbStorageManager(this)
        volumes = storageManager.discoverMountedVolumes()

        binding.buttonCancel.setOnClickListener { finish() }
        binding.buttonUp.setOnClickListener { navigateUp() }
        binding.buttonSelect.setOnClickListener {
            currentDir?.let { finishWithSelection(it) }
        }
        binding.buttonSafFallback.setOnClickListener { launchSafFallback() }

        binding.folderList.setOnItemClickListener { _, _, position, _ ->
            val name = binding.folderList.adapter.getItem(position) as? String ?: return@setOnItemClickListener
            if (name == getString(R.string.folder_picker_empty) ||
                name == getString(R.string.folder_picker_no_usb)
            ) {
                return@setOnItemClickListener
            }
            enterChild(name)
        }

        openStartLocation()
    }

    private fun openStartLocation() {
        when {
            volumes.isEmpty() -> {
                showingVolumeList = false
                currentVolume = null
                currentDir = null
                render()
            }
            volumes.size == 1 -> {
                showingVolumeList = false
                currentVolume = volumes.first()
                currentDir = File(volumes.first().absolutePath)
                render()
            }
            else -> showVolumeList()
        }
    }

    private fun showVolumeList() {
        showingVolumeList = true
        currentVolume = null
        currentDir = null
        render()
    }

    private fun enterChild(name: String) {
        if (showingVolumeList) {
            val volume = volumes.find { displayVolume(it) == name } ?: return
            showingVolumeList = false
            currentVolume = volume
            currentDir = File(volume.absolutePath)
            render()
            return
        }
        val parent = currentDir ?: return
        val child = File(parent, name)
        if (storageManager.isReadableDirectory(child.absolutePath)) {
            currentDir = child
            if (currentVolume == null) {
                currentVolume = storageManager.volumeContaining(child.absolutePath, volumes)
            }
            render()
        }
    }

    private fun navigateUp() {
        if (showingVolumeList) {
            return
        }
        val dir = currentDir ?: return
        val volume = currentVolume
        if (volume != null && MusicFolderPaths.isSamePath(dir.absolutePath, volume.absolutePath)) {
            if (volumes.size > 1) {
                showVolumeList()
            }
            return
        }
        val parent = dir.parentFile
        if (parent != null && storageManager.isReadableDirectory(parent.absolutePath)) {
            currentDir = parent
            render()
        }
    }

    private fun render() {
        if (volumes.isEmpty()) {
            binding.pathValue.setText(R.string.usb_status_waiting)
            binding.buttonSelect.isEnabled = false
            binding.buttonUp.isEnabled = false
            binding.folderList.adapter = ArrayAdapter(
                this,
                R.layout.item_folder_row,
                R.id.folderName,
                listOf(getString(R.string.folder_picker_no_usb))
            )
            return
        }

        if (showingVolumeList) {
            binding.pathValue.setText(R.string.folder_picker_choose_volume)
            binding.buttonSelect.isEnabled = false
            binding.buttonUp.isEnabled = false
            val names = volumes.map { displayVolume(it) }
            binding.folderList.adapter = ArrayAdapter(this, R.layout.item_folder_row, R.id.folderName, names)
            return
        }

        val dir = currentDir
        if (dir == null) {
            binding.buttonSelect.isEnabled = false
            return
        }

        binding.pathValue.text = dir.absolutePath
        binding.buttonSelect.isEnabled = true
        val atVolumeRoot = currentVolume?.let { MusicFolderPaths.isSamePath(dir.absolutePath, it.absolutePath) } == true
        binding.buttonUp.isEnabled = !atVolumeRoot || volumes.size > 1

        val children = storageManager.listSubdirectories(dir).map { it.name }
        binding.folderList.adapter = ArrayAdapter(
            this,
            R.layout.item_folder_row,
            R.id.folderName,
            if (children.isEmpty()) listOf(getString(R.string.folder_picker_empty)) else children
        )
    }

    private fun displayVolume(volume: UsbVolume): String {
        val label = volume.label?.takeIf { it.isNotBlank() } ?: "USB"
        return "$label  (${volume.absolutePath})"
    }

    private fun finishWithSelection(folder: File) {
        val volume = currentVolume ?: storageManager.volumeContaining(folder.absolutePath, volumes)
        val record = MusicFolderRecord.fromSelection(folder.absolutePath, volume)
        val data = Intent().apply {
            putExtra(EXTRA_ABSOLUTE_PATH, record.absolutePath)
            putExtra(EXTRA_RELATIVE_PATH, record.relativePath)
            putExtra(EXTRA_VOLUME_UUID, record.volumeUuid)
            putExtra(EXTRA_VOLUME_LABEL, record.volumeLabel)
            putExtra(EXTRA_FOLDER_NAME, record.folderName)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun launchSafFallback() {
        try {
            safLauncher.launch(SafTreeFallback.createOpenTreeIntent())
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.saf_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.saf_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_ABSOLUTE_PATH = "absolute_path"
        const val EXTRA_RELATIVE_PATH = "relative_path"
        const val EXTRA_VOLUME_UUID = "volume_uuid"
        const val EXTRA_VOLUME_LABEL = "volume_label"
        const val EXTRA_FOLDER_NAME = "folder_name"

        fun recordFromResult(data: Intent?): MusicFolderRecord? {
            val absolute = data?.getStringExtra(EXTRA_ABSOLUTE_PATH) ?: return null
            return MusicFolderRecord(
                absolutePath = absolute,
                relativePath = data.getStringExtra(EXTRA_RELATIVE_PATH) ?: "",
                volumeUuid = data.getStringExtra(EXTRA_VOLUME_UUID),
                volumeLabel = data.getStringExtra(EXTRA_VOLUME_LABEL),
                folderName = data.getStringExtra(EXTRA_FOLDER_NAME) ?: ""
            )
        }
    }
}
