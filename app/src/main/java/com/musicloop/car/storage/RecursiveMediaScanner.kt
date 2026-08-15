package com.musicloop.car.storage

import java.io.File

/**
 * Read-only recursive media scan for removable USB volumes (Phase 1 PoC).
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
    private val enumerator = MediaEnumerator(
        listChildren = listChildren,
        isDirectory = isDirectory,
        isSymlink = isSymlink,
        fileName = fileName,
        fileLength = fileLength,
        filePath = filePath
    )

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

        val result = enumerator.collect(root, maxDepth, maxFiles)
        for (item in result.files) {
            val pass = readProbe(item.file)
            if (item.mediaType == MediaKind.AUDIO) {
                audioCount += 1
                audioByExt[item.extension] = (audioByExt[item.extension] ?: 0) + 1
            } else {
                videoCount += 1
                videoByExt[item.extension] = (videoByExt[item.extension] ?: 0) + 1
            }
            if (samples.size < sampleLimit) {
                samples += MediaReadSample(
                    absolutePath = filePath(item.file),
                    kind = item.mediaType,
                    sizeBytes = item.sizeBytes,
                    streamReadPass = pass
                )
            }
        }
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
        fun isSymbolicLink(file: File): Boolean = MediaEnumerator.isSymbolicLink(file)
    }
}
