package com.musicloop.car.scanner

import com.musicloop.car.storage.MusicFolderPaths
import java.io.File
import java.io.IOException

/**
 * Read-only filesystem probe. Uses listFiles / length / lastModified only.
 */
class FilesystemAudioFileProbe(
    private val volumeRootProvider: () -> String?,
    private val isCancelled: () -> Boolean
) : AudioFileProbe {

    override fun listAudioFiles(folderAbsolute: String, volumeRoot: String): List<DiscoveredFile> {
        val found = mutableListOf<DiscoveredFile>()
        walk(File(folderAbsolute), volumeRoot, 0, found)
        return found
    }

    override fun snapshot(absolutePath: String): FileSnapshot? {
        return try {
            val file = File(absolutePath)
            if (!file.exists()) {
                FileSnapshot(exists = false, size = 0L, lastModified = 0L)
            } else {
                FileSnapshot(
                    exists = true,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun isVolumePresent(): Boolean {
        val root = volumeRootProvider() ?: return false
        return try {
            File(root).isDirectory
        } catch (_: Exception) {
            false
        }
    }

    private fun walk(
        dir: File,
        volumeRoot: String,
        depth: Int,
        out: MutableList<DiscoveredFile>
    ) {
        if (isCancelled()) {
            throw IOException("scan cancelled")
        }
        if (depth > ScanPolicy.MAX_DIRECTORY_DEPTH) {
            return
        }
        val children = dir.listFiles() ?: throw IOException("unreadable directory: ${dir.path}")
        for (child in children) {
            if (isCancelled()) {
                throw IOException("scan cancelled")
            }
            val isDirectory = try {
                child.isDirectory
            } catch (_: Exception) {
                continue
            }
            if (isDirectory) {
                walk(child, volumeRoot, depth + 1, out)
                continue
            }
            if (!AudioFileFilter.isAudioFile(child.name)) {
                continue
            }
            val relative = MusicFolderPaths.relativeToVolume(volumeRoot, child.absolutePath) ?: continue
            val snapshot = snapshot(child.absolutePath) ?: continue
            if (!snapshot.exists) {
                continue
            }
            out += DiscoveredFile(
                relativePath = relative,
                filename = child.name,
                extension = AudioFileFilter.extensionOf(child.name) ?: "",
                size = snapshot.size,
                lastModified = snapshot.lastModified
            )
        }
    }
}
