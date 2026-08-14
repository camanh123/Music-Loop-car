package com.musicloop.car.scanner

import com.musicloop.car.storage.MusicFolderPaths
import java.io.File

/**
 * Read-only filesystem probe. Uses listFiles / list / length / lastModified only.
 *
 * Walks by child names so relative paths stay valid even when File.absolutePath
 * does not share the volumeRoot prefix (CARFU symlink / USB1 vs media_rw).
 *
 * An unreadable child directory is logged and skipped. Only the selected scan
 * root being unreadable aborts enumeration.
 */
class FilesystemAudioFileProbe(
    private val volumeRootProvider: () -> String?,
    private val isCancelled: () -> Boolean,
    private val listChildren: (File) -> Array<File>? = Companion::listChildrenReadOnly
) : AudioFileProbe {

    override fun listAudioFiles(folderAbsolute: String, volumeRoot: String): EnumerationResult {
        val folder = File(folderAbsolute)
        val exists = try {
            folder.exists()
        } catch (error: Exception) {
            ScannerLog.error("folder.exists", error)
            false
        }
        val isDirectory = try {
            folder.isDirectory
        } catch (error: Exception) {
            ScannerLog.error("folder.isDirectory", error)
            false
        }
        val canRead = try {
            folder.canRead()
        } catch (error: Exception) {
            ScannerLog.error("folder.canRead", error)
            false
        }
        val absolute = try {
            folder.absolutePath
        } catch (_: Exception) {
            folderAbsolute
        }

        ScannerLog.i("folder exists=$exists")
        ScannerLog.i("folder isDirectory=$isDirectory")
        ScannerLog.i("folder canRead=$canRead")
        ScannerLog.i("folder absolutePath=$absolute")
        ScannerLog.i("ENUMERATION_START")

        val found = mutableListOf<DiscoveredFile>()
        var totalEntries = 0
        var audioCandidates = 0
        var rejected = 0

        if (!exists || !isDirectory) {
            ScannerLog.w("ENUMERATION_RESULT root missing or not a directory path=$absolute")
            return EnumerationResult(
                files = emptyList(),
                totalFilesystemEntries = 0,
                audioCandidates = 0,
                acceptedAudioFiles = 0,
                rejectedFiles = 0,
                folderExists = exists,
                folderIsDirectory = isDirectory,
                folderCanRead = canRead,
                folderAbsolutePath = absolute,
                rootUnreadable = true
            )
        }

        val folderRelative = MusicFolderPaths.relativeToVolume(volumeRoot, folderAbsolute)
            ?: File(MusicFolderPaths.normalizeAbsolute(folderAbsolute)).name.orEmpty()

        val rootUnreadable = walk(
            dir = folder,
            relativeFromVolume = folderRelative,
            depth = 0,
            out = found,
            onEntry = { totalEntries += 1 },
            onAudioCandidate = { audioCandidates += 1 },
            onRejected = { rejected += 1 }
        )

        ScannerLog.i(
            "ENUMERATION_RESULT total filesystem entries=$totalEntries " +
                "audio candidates=$audioCandidates " +
                "accepted audio files=${found.size} " +
                "rejected files=$rejected"
        )

        return EnumerationResult(
            files = found.toList(),
            totalFilesystemEntries = totalEntries,
            audioCandidates = audioCandidates,
            acceptedAudioFiles = found.size,
            rejectedFiles = rejected,
            folderExists = exists,
            folderIsDirectory = isDirectory,
            folderCanRead = canRead,
            folderAbsolutePath = absolute,
            rootUnreadable = rootUnreadable
        )
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
        } catch (error: Exception) {
            ScannerLog.error("snapshot", error)
            null
        }
    }

    override fun isVolumePresent(): Boolean {
        val root = volumeRootProvider() ?: return false
        return try {
            File(root).isDirectory
        } catch (error: Exception) {
            ScannerLog.error("isVolumePresent", error)
            false
        }
    }

    /**
     * @return true when the selected root directory itself could not be listed.
     */
    private fun walk(
        dir: File,
        relativeFromVolume: String,
        depth: Int,
        out: MutableList<DiscoveredFile>,
        onEntry: () -> Unit,
        onAudioCandidate: () -> Unit,
        onRejected: () -> Unit
    ): Boolean {
        if (isCancelled()) {
            return depth == 0
        }
        if (depth > ScanPolicy.MAX_DIRECTORY_DEPTH) {
            ScannerLog.w("skip directory past max depth path=${safePath(dir)}")
            return false
        }

        val children = try {
            listChildren(dir)
        } catch (error: Exception) {
            ScannerLog.error("listChildren ${safePath(dir)}", error)
            null
        }

        if (children == null) {
            ScannerLog.w("unreadable directory path=${safePath(dir)} depth=$depth")
            return depth == 0
        }

        for (child in children) {
            if (isCancelled()) {
                return depth == 0
            }
            val name = try {
                child.name
            } catch (error: Exception) {
                ScannerLog.error("child.name", error)
                onRejected()
                continue
            }
            if (name == "." || name == "..") {
                continue
            }

            onEntry()
            val isDirectory = try {
                child.isDirectory
            } catch (error: Exception) {
                ScannerLog.error("child.isDirectory $name", error)
                logEntry(name, child, isDirectory = false, size = -1L, extension = "", accepted = false)
                onRejected()
                continue
            }
            val childRelative = if (relativeFromVolume.isEmpty()) name else "$relativeFromVolume/$name"
            val childAbsolute = try {
                child.absolutePath
            } catch (_: Exception) {
                childRelative
            }
            val size = try {
                if (isDirectory) 0L else child.length()
            } catch (_: Exception) {
                -1L
            }
            val extension = AudioFileFilter.extensionOf(name) ?: ""

            if (isDirectory) {
                logEntry(name, child, isDirectory = true, size = size, extension = extension, accepted = false)
                walk(
                    dir = child,
                    relativeFromVolume = childRelative,
                    depth = depth + 1,
                    out = out,
                    onEntry = onEntry,
                    onAudioCandidate = onAudioCandidate,
                    onRejected = onRejected
                )
                continue
            }

            val accepted = AudioFileFilter.isAudioFile(name)
            logEntry(name, child, isDirectory = false, size = size, extension = extension, accepted = accepted)
            if (!accepted) {
                onRejected()
                continue
            }
            onAudioCandidate()

            val snapshot = snapshot(childAbsolute)
            if (snapshot == null || !snapshot.exists) {
                ScannerLog.w("rejected audio candidate missing snapshot path=$childAbsolute")
                onRejected()
                continue
            }
            out += DiscoveredFile(
                relativePath = childRelative,
                filename = name,
                extension = AudioFileFilter.extensionOf(name) ?: "",
                size = snapshot.size,
                lastModified = snapshot.lastModified,
                absolutePath = childAbsolute
            )
        }
        return false
    }

    private fun logEntry(
        filename: String,
        file: File,
        isDirectory: Boolean,
        size: Long,
        extension: String,
        accepted: Boolean
    ) {
        val absolute = try {
            file.absolutePath
        } catch (_: Exception) {
            filename
        }
        val decision = when {
            isDirectory -> "directory"
            accepted -> "accepted"
            else -> "rejected"
        }
        ScannerLog.i(
            "entry filename=$filename absolutePath=$absolute isDirectory=$isDirectory " +
                "size=$size extension=$extension $decision"
        )
    }

    private fun safePath(file: File): String {
        return try {
            file.absolutePath
        } catch (_: Exception) {
            file.path
        }
    }

    companion object {
        fun listChildrenReadOnly(dir: File): Array<File>? {
            val listed = dir.listFiles()
            if (listed != null) {
                return listed
            }
            val names = dir.list() ?: return null
            return Array(names.size) { index -> File(dir, names[index]) }
        }
    }
}
