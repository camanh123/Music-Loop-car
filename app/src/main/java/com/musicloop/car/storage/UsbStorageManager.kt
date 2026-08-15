package com.musicloop.car.storage

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * Enumerates every StorageVolume the system reports. Read-only.
 *
 * Does not hard-code USB1/USB2. Does not use SAF / DocumentsUI.
 * Media recursion runs only on removable volumes whose state is mounted.
 */
class UsbStorageManager(
    context: Context,
    private val scanner: RecursiveMediaScanner = RecursiveMediaScanner()
) {
    private val appContext = context.applicationContext

    fun inspectAllVolumes(): List<VolumeReport> {
        val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            ?: return emptyList()
        val volumes = try {
            storageManager.storageVolumes
        } catch (_: Exception) {
            emptyList()
        }
        return volumes.mapIndexed { index, volume -> inspectVolume(index + 1, volume) }
    }

    private fun inspectVolume(index: Int, volume: StorageVolume): VolumeReport {
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
        val listFilesNonNull = listed != null
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

        val forbiddenRoot = ScanPolicy.isForbiddenScanRoot(rootPath.orEmpty())
        val shouldScan = removable && isMounted(state) && root != null && listFilesNonNull && !forbiddenRoot
        val media = if (shouldScan && root != null) {
            try {
                scanner.scan(root)
            } catch (_: Exception) {
                MediaScanResult(scanned = false, skipReason = "scan error")
            }
        } else {
            val reason = when {
                !removable -> "not removable"
                !isMounted(state) -> "not mounted"
                root == null -> "root unresolved"
                forbiddenRoot -> "internal/forbidden"
                else -> "directory not listable"
            }
            MediaScanResult(scanned = false, skipReason = reason)
        }

        val checks = VerificationChecks.evaluate(
            volumePresent = true,
            rootPath = rootPath,
            exists = exists,
            isDirectory = isDirectory,
            canRead = canRead,
            listFilesNonNull = listFilesNonNull,
            mediaFilesReadable = media.mediaFilesReadable
        )
        return VolumeReport(
            index = index,
            description = description,
            state = state,
            removableCandidate = removable,
            isPrimary = primary,
            uuid = uuid,
            rootPath = rootPath,
            exists = exists,
            canRead = canRead,
            isDirectory = isDirectory,
            listFilesNonNull = listFilesNonNull,
            totalSpaceBytes = totalSpace,
            freeSpaceBytes = freeSpace,
            checks = checks,
            media = media
        )
    }

    private fun isMounted(state: String): Boolean {
        return state == Environment.MEDIA_MOUNTED ||
            state == Environment.MEDIA_MOUNTED_READ_ONLY
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
