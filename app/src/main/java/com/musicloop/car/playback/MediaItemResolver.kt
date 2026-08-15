package com.musicloop.car.playback

import com.musicloop.car.storage.MediaExtensions
import com.musicloop.car.storage.VolumeSnapshot
import java.io.File

/**
 * Resolves [volumeId] + [relativePath] against the current StorageManager snapshot.
 * Absolute mount paths are runtime-only.
 */
class MediaItemResolver(
    private val snapshotVolumes: () -> List<VolumeSnapshot>,
    private val fileReadable: (File) -> Boolean = { file ->
        try {
            file.exists() && file.isFile && file.canRead()
        } catch (_: Exception) {
            false
        }
    }
) {
    fun resolve(volumeId: String, relativePath: String): ResolveResult {
        if (volumeId.isBlank() || relativePath.isBlank()) {
            return ResolveResult.Invalid("missing identity")
        }
        if (MediaExtensions.kindOf(relativePath.substringAfterLast('/').ifBlank { relativePath }) == null) {
            return ResolveResult.Unsupported("unsupported media type")
        }
        val snapshots = try {
            snapshotVolumes()
        } catch (_: Exception) {
            emptyList()
        }
        val volume = snapshots.firstOrNull { snapshot ->
            snapshot.volumeId == volumeId && snapshot.presentMountedRemovable
        } ?: return ResolveResult.Offline(volumeId)
        val root = volume.rootPath
        if (root.isNullOrBlank()) {
            return ResolveResult.Invalid("root unresolved")
        }
        val absolute = MediaPaths.join(root, relativePath)
            ?: return ResolveResult.Invalid("invalid relative path")
        val file = File(absolute)
        return try {
            if (fileReadable(file)) {
                ResolveResult.Ready(absolutePath = absolute, rootPath = root)
            } else {
                ResolveResult.Missing(absolute)
            }
        } catch (_: Exception) {
            ResolveResult.Missing(absolute)
        }
    }
}
