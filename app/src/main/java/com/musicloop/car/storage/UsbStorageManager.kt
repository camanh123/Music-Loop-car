package com.musicloop.car.storage

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * Discovers mounted removable USB volumes without hard-coded mount names.
 * Read-only: never creates files, directories, or USB metadata.
 */
class UsbStorageManager(context: Context) {

    private val appContext = context.applicationContext

    fun discoverMountedVolumes(): List<UsbVolume> {
        return try {
            val fromManager = discoverFromStorageManager()
            if (fromManager.isNotEmpty()) {
                fromManager
            } else {
                discoverFromFilesystem()
            }
        } catch (_: Exception) {
            try {
                discoverFromFilesystem()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun volumeContaining(absolutePath: String, volumes: List<UsbVolume> = discoverMountedVolumes()): UsbVolume? {
        return volumes
            .filter { MusicFolderPaths.isSameOrChildPath(absolutePath, it.absolutePath) }
            .maxByOrNull { it.absolutePath.length }
    }

    fun listSubdirectories(directory: File): List<File> {
        return try {
            if (!directory.isDirectory) {
                emptyList()
            } else {
                directory.listFiles()
                    ?.filter { file ->
                        try {
                            file.isDirectory
                        } catch (_: Exception) {
                            false
                        }
                    }
                    ?.sortedBy { it.name.lowercase() }
                    ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isReadableDirectory(path: String): Boolean {
        return try {
            val file = File(path)
            file.isDirectory && file.canRead()
        } catch (_: Exception) {
            false
        }
    }

    private fun discoverFromStorageManager(): List<UsbVolume> {
        val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            ?: return emptyList()
        val primaryPath = primaryStoragePath()
        return storageManager.storageVolumes.mapNotNull { volume ->
            volumeToUsb(volume, primaryPath)
        }.distinctBy { MusicFolderPaths.normalizeAbsolute(it.absolutePath).lowercase() }
    }

    private fun volumeToUsb(volume: StorageVolume, primaryPath: String?): UsbVolume? {
        return try {
            if (volume.isPrimary) {
                return null
            }
            val state = volume.state ?: return null
            if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                return null
            }
            val path = volumePath(volume) ?: return null
            val file = File(path)
            if (!file.isDirectory) {
                return null
            }
            val canonical = canonicalOrAbsolute(file)
            if (isEmulatedOrPrimary(canonical, primaryPath)) {
                return null
            }
            UsbVolume(
                uuid = volume.uuid,
                label = volume.getDescription(appContext),
                absolutePath = canonical,
                state = state,
                isRemovable = volume.isRemovable
            )
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("PrivateApi")
    private fun volumePath(volume: StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                volume.directory?.absolutePath?.let { return it }
            } catch (_: Exception) {
                // Fall through to hidden API / filesystem inspection.
            }
        }
        try {
            val method = StorageVolume::class.java.getMethod("getPath")
            (method.invoke(volume) as? String)?.let { return it }
        } catch (_: Exception) {
            // Continue.
        }
        return try {
            val field = StorageVolume::class.java.getDeclaredField("mPath")
            field.isAccessible = true
            when (val value = field.get(volume)) {
                is File -> value.absolutePath
                is String -> value
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun discoverFromFilesystem(): List<UsbVolume> {
        val primaryPath = primaryStoragePath()
        val roots = listOf("/storage", "/mnt/media_rw", "/mnt/usb")
        val found = mutableListOf<UsbVolume>()
        for (rootPath in roots) {
            val root = File(rootPath)
            val children = try {
                root.listFiles() ?: continue
            } catch (_: Exception) {
                continue
            }
            for (child in children) {
                try {
                    if (!child.isDirectory) continue
                    val name = child.name
                    if (name in SKIP_DIR_NAMES) continue
                    val canonical = canonicalOrAbsolute(child)
                    if (isEmulatedOrPrimary(canonical, primaryPath)) continue
                    if (!child.canRead()) continue
                    found += UsbVolume(
                        uuid = MusicFolderPaths.volumeIdentityFromPath(canonical),
                        label = name,
                        absolutePath = canonical,
                        state = Environment.MEDIA_MOUNTED,
                        isRemovable = true
                    )
                } catch (_: Exception) {
                    // Skip unreadable entries; never crash on a bad USB FS.
                }
            }
        }
        return found.distinctBy { MusicFolderPaths.normalizeAbsolute(it.absolutePath).lowercase() }
    }

    private fun primaryStoragePath(): String? {
        return try {
            Environment.getExternalStorageDirectory()?.let { canonicalOrAbsolute(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun isEmulatedOrPrimary(path: String, primaryPath: String?): Boolean {
        val normalized = MusicFolderPaths.normalizeAbsolute(path).lowercase()
        if (normalized.contains("/emulated/")) {
            return true
        }
        if (primaryPath != null && MusicFolderPaths.isSamePath(path, primaryPath)) {
            return true
        }
        return normalized.endsWith("/sdcard") || normalized.endsWith("/sdcard0")
    }

    private fun canonicalOrAbsolute(file: File): String {
        return try {
            MusicFolderPaths.normalizeAbsolute(file.canonicalPath)
        } catch (_: Exception) {
            MusicFolderPaths.normalizeAbsolute(file.absolutePath)
        }
    }

    companion object {
        private val SKIP_DIR_NAMES = setOf(
            ".",
            "..",
            "emulated",
            "self",
            "enc_emulated",
            "sdcard0"
        )
    }
}
