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
 * Does not probe [File.canWrite] — write capability is delete-path only.
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
        val candidates = collectRootCandidates(volume, uuid)
        val rootPath = UsbRootResolver.selectRoot(candidates) { path -> probeRoot(path) }
        val root = rootPath?.let { File(it) }
        val exists = flag { root?.exists() == true }
        val isDirectory = flag { root?.isDirectory == true }
        val canRead = flag { root?.canRead() == true }
        val listed = try {
            root?.listFiles()
        } catch (_: Exception) {
            null
        }
        if (removable && !primary) {
            com.musicloop.car.library.LibraryDiagnostics.log(
                "root chosen=${rootPath ?: "-"} candidates=${candidates.size} " +
                    "exists=$exists dir=$isDirectory listFiles=${listed != null} " +
                    "uuid=${uuid ?: "-"}"
            )
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
     * Never invents USB1/USB2 paths. Prefer [snapshotVolume], which picks a listable alias.
     */
    @SuppressLint("PrivateApi")
    fun resolveRootPath(volume: StorageVolume): String? {
        return collectRootCandidates(volume, uuidOf(volume)).firstOrNull()
    }

    @SuppressLint("PrivateApi")
    fun collectRootCandidates(volume: StorageVolume, uuid: String? = uuidOf(volume)): List<String> {
        return UsbRootResolver.uniqueCandidates(
            listOf(
                directoryPath(volume),
                hiddenString(volume, "getPath"),
                hiddenFileOrString(volume, "mPath"),
                hiddenString(volume, "getInternalPath"),
                hiddenFileOrString(volume, "mInternalPath"),
                UsbRootResolver.storageUuidCandidate(uuid)
            )
        )
    }

    private fun probeRoot(path: String): UsbRootResolver.Probe {
        val root = File(path)
        val exists = flag { root.exists() }
        val isDirectory = flag { root.isDirectory }
        val listed = try {
            root.listFiles()
        } catch (_: Exception) {
            null
        }
        return UsbRootResolver.Probe(
            exists = exists,
            isDirectory = isDirectory,
            listFilesNonNull = listed != null
        )
    }

    private fun directoryPath(volume: StorageVolume): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        return try {
            volume.directory?.absolutePath?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun uuidOf(volume: StorageVolume): String? {
        return try {
            volume.uuid?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("PrivateApi")
    private fun hiddenString(volume: StorageVolume, methodName: String): String? {
        return try {
            val method = StorageVolume::class.java.getMethod(methodName)
            (method.invoke(volume) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("PrivateApi")
    private fun hiddenFileOrString(volume: StorageVolume, fieldName: String): String? {
        return try {
            val field = StorageVolume::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            when (val value = field.get(volume)) {
                is File -> value.absolutePath.takeIf { it.isNotBlank() }
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
