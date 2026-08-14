package com.musicloop.car.player

import com.musicloop.car.storage.MusicFolderPaths
import java.io.File

/**
 * Resolve a playable USB path from the currently mounted volume.
 * Never uses a stale USB1 absolute path after remount as USB2.
 * Read-only: exists / isFile / canRead only.
 */
object PlaybackPathResolver : TrackFileAccess {

    fun resolveAbsolute(volumeRoot: String?, relativePath: String): String? {
        if (volumeRoot.isNullOrBlank() || relativePath.isBlank()) {
            return null
        }
        return MusicFolderPaths.join(volumeRoot, relativePath)
    }

    fun isReadableFile(absolutePath: String): Boolean {
        return try {
            val file = File(absolutePath)
            file.exists() && file.isFile && file.canRead()
        } catch (_: Exception) {
            false
        }
    }

    override fun resolveReadable(volumeRoot: String?, relativePath: String): String? {
        val absolute = resolveAbsolute(volumeRoot, relativePath) ?: return null
        return if (isReadableFile(absolute)) absolute else null
    }
}
