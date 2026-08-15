package com.musicloop.car

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.musicloop.car.databinding.ActivityMainBinding
import com.musicloop.car.storage.CapabilityReportFormatter
import com.musicloop.car.storage.DeviceInfo
import com.musicloop.car.storage.UsbStorageManager
import java.util.concurrent.Executors

/**
 * Phase 1 USB capability PoC. Landscape report only. No playback, Room, or SAF.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runScan()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            binding.reportText.setText(R.string.permission_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buttonScan.setOnClickListener { onScanClicked() }
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun onScanClicked() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runScan()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun runScan() {
        binding.buttonScan.isEnabled = false
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
                binding.buttonScan.isEnabled = true
            }
        }
    }

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
}
