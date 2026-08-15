package com.musicloop.car.storage

import java.io.File
import java.nio.file.Files

/**
 * Read-only recursive media scan for removable USB volumes.
 *
 * Does not follow symlinks, does not enter hidden directories, does not scan
 * internal /data /system /emulated roots. Caps depth and file count.
 */
class RecursiveMediaScanner(
    private val listChildren: (File) -> Array<File>? = { dir ->
        try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        }
    },
    private val isDirectory: (File) -> Boolean = { file ->
        try {
            file.isDirectory
        } catch (_: Exception) {
            false
        }
    },
    private val isSymlink: (File) -> Boolean = Companion::isSymbolicLink,
    private val fileName: (File) -> String = { file ->
        try {
            file.name
        } catch (_: Exception) {
            ""
        }
    },
    private val fileLength: (File) -> Long = { file ->
        try {
            file.length()
        } catch (_: Exception) {
            0L
        }
    },
    private val filePath: (File) -> String = { file ->
        try {
            file.absolutePath
        } catch (_: Exception) {
            file.path
        }
    },
    private val readProbe: (File) -> Boolean = { StreamReadTester.canReadBytes(it) },
    private val maxDepth: Int = ScanPolicy.MAX_DEPTH,
    private val maxFiles: Int = ScanPolicy.MAX_FILES,
    private val sampleLimit: Int = ScanPolicy.SAMPLE_LIMIT
) {

    fun scan(root: File): MediaScanResult {
        val rootPath = filePath(root)
        if (ScanPolicy.isForbiddenScanRoot(rootPath)) {
            return MediaScanResult(scanned = false, skipReason = "forbidden root")
        }
        val audioByExt = linkedMapOf<String, Int>()
        val videoByExt = linkedMapOf<String, Int>()
        val samples = mutableListOf<MediaReadSample>()
        var audioCount = 0
        var videoCount = 0
        var collected = 0

        fun walk(dir: File, depth: Int) {
            if (collected >= maxFiles) {
                return
            }
            if (depth > maxDepth) {
                return
            }
            val path = filePath(dir)
            if (ScanPolicy.isForbiddenScanRoot(path)) {
                return
            }
            // Volume roots on CARFU are often mount symlinks (USB1 -> media_rw).
            // Skip symlink *children* only; still list the StorageManager root.
            if (depth > 0 && isSymlink(dir)) {
                return
            }
            val children = listChildren(dir) ?: return
            for (child in children) {
                if (collected >= maxFiles) {
                    return
                }
                val name = fileName(child)
                if (name.isEmpty() || name == "." || name == "..") {
                    continue
                }
                if (ScanPolicy.isHiddenName(name)) {
                    continue
                }
                if (isSymlink(child)) {
                    continue
                }
                if (isDirectory(child)) {
                    walk(child, depth + 1)
                    continue
                }
                val kind = MediaExtensions.kindOf(name) ?: continue
                val ext = MediaExtensions.extensionOf(name) ?: continue
                val pass = readProbe(child)
                val size = fileLength(child)
                if (kind == MediaKind.AUDIO) {
                    audioCount += 1
                    audioByExt[ext] = (audioByExt[ext] ?: 0) + 1
                } else {
                    videoCount += 1
                    videoByExt[ext] = (videoByExt[ext] ?: 0) + 1
                }
                if (samples.size < sampleLimit) {
                    samples += MediaReadSample(
                        absolutePath = filePath(child),
                        kind = kind,
                        sizeBytes = size,
                        streamReadPass = pass
                    )
                }
                collected += 1
            }
        }

        walk(root, 0)
        return MediaScanResult(
            audioCount = audioCount,
            videoCount = videoCount,
            audioByExtension = audioByExt.toMap(),
            videoByExtension = videoByExt.toMap(),
            samples = samples.toList(),
            scanned = true
        )
    }

    companion object {
        fun isSymbolicLink(file: File): Boolean {
            try {
                if (Files.isSymbolicLink(file.toPath())) {
                    return true
                }
            } catch (_: Exception) {
                // android.jar unit-test stubs may no-op NIO; fall through.
            }
            return try {
                val parent = file.parentFile ?: return false
                val canonicalChild = file.canonicalFile
                val canonicalParent = parent.canonicalFile
                canonicalChild.parentFile != canonicalParent ||
                    !canonicalChild.name.equals(file.name, ignoreCase = false)
            } catch (_: Exception) {
                false
            }
        }
    }
}
