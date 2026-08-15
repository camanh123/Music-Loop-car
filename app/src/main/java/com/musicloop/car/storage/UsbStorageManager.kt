package com.musicloop.car.storage

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * Enumerates every StorageVolume the system reports. Read-only.
 *
 * Does not hard-code USB1/USB2. Does not use SAF / DocumentsUI.
 * Media recursion for the Phase 1 PoC runs only on removable mounted volumes.
 * Phase 2A library scans use [snapshotVolumes] plus LibraryMediaScanner.
 */
class UsbStorageManager(
    context: Context,
    private val scanner: RecursiveMediaScanner = RecursiveMediaScanner()
) {
    private val appContext = context.applicationContext

    fun snapshotVolumes(): List<VolumeSnapshot> {
        val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            ?: return emptyList()
        val volumes = try {
            storageManager.storageVolumes
        } catch (_: Exception) {
            emptyList()
        }
        return volumes.map { volume -> snapshotVolume(volume) }
    }

    fun inspectAllVolumes(): List<VolumeReport> {
        return snapshotVolumes().mapIndexed { index, snapshot ->
            inspectSnapshot(index + 1, snapshot)
        }
    }

    fun snapshotVolume(volume: StorageVolume): VolumeSnapshot {
        val description = try {
            volume.getDescription(appContext)?.takeIf { it.isNotBlank() } ?: "N/A"
        } catch (_: Exception) {
            "N/A"
        }
        val state = try {
            volume.state ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        val removable = try {
            volume.isRemovable
        } catch (_: Exception) {
            false
        }
        val primary = try {
            volume.isPrimary
        } catch (_: Exception) {
            false
        }
        val uuid = try {
            volume.uuid?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        val rootPath = resolveRootPath(volume)
        val root = rootPath?.let { File(it) }
        val exists = flag { root?.exists() == true }
        val isDirectory = flag { root?.isDirectory == true }
        val canRead = flag { root?.canRead() == true }
        val listed = try {
            root?.listFiles()
        } catch (_: Exception) {
            null
        }
        val totalSpace = try {
            root?.totalSpace ?: 0L
        } catch (_: Exception) {
            0L
        }
        val freeSpace = try {
            root?.freeSpace ?: 0L
        } catch (_: Exception) {
            0L
        }
        return VolumeSnapshot(
            description = description,
            state = state,
            removable = removable,
            isPrimary = primary,
            uuid = uuid,
            rootPath = rootPath,
            exists = exists,
            isDirectory = isDirectory,
            canRead = canRead,
            listFilesNonNull = listed != null,
            totalSpaceBytes = totalSpace,
            freeSpaceBytes = freeSpace
        )
    }

    private fun inspectSnapshot(index: Int, snapshot: VolumeSnapshot): VolumeReport {
        val shouldScan = snapshot.scannable
        val media = if (shouldScan && snapshot.rootPath != null) {
            try {
                scanner.scan(File(snapshot.rootPath))
            } catch (_: Exception) {
                MediaScanResult(scanned = false, skipReason = "scan error")
            }
        } else {
            MediaScanResult(scanned = false, skipReason = snapshot.skipReason)
        }
        val checks = VerificationChecks.evaluate(
            volumePresent = true,
            rootPath = snapshot.rootPath,
            exists = snapshot.exists,
            isDirectory = snapshot.isDirectory,
            canRead = snapshot.canRead,
            listFilesNonNull = snapshot.listFilesNonNull,
            mediaFilesReadable = media.mediaFilesReadable
        )
        return VolumeReport(
            index = index,
            description = snapshot.description,
            state = snapshot.state,
            removableCandidate = snapshot.removable,
            isPrimary = snapshot.isPrimary,
            uuid = snapshot.uuid,
            rootPath = snapshot.rootPath,
            exists = snapshot.exists,
            canRead = snapshot.canRead,
            isDirectory = snapshot.isDirectory,
            listFilesNonNull = snapshot.listFilesNonNull,
            totalSpaceBytes = snapshot.totalSpaceBytes,
            freeSpaceBytes = snapshot.freeSpaceBytes,
            checks = checks,
            media = media
        )
    }

    /**
     * API 29 CARFU-safe root resolution: directory (API 30+), then getPath(), then mPath.
     * Never invents USB1/USB2 paths.
     */
    @SuppressLint("PrivateApi")
    fun resolveRootPath(volume: StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                volume.directory?.absolutePath?.takeIf { it.isNotBlank() }?.let { return it }
            } catch (_: Exception) {
                // Fall through to hidden API used on Android 10.
            }
        }
        try {
            val method = StorageVolume::class.java.getMethod("getPath")
            val path = method.invoke(volume) as? String
            if (!path.isNullOrBlank()) {
                return path
            }
        } catch (_: Exception) {
            // Continue.
        }
        return try {
            val field = StorageVolume::class.java.getDeclaredField("mPath")
            field.isAccessible = true
            when (val value = field.get(volume)) {
                is File -> value.absolutePath
                is String -> value.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun flag(block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (_: Exception) {
            false
        }
    }
}
