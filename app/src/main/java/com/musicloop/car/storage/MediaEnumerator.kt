package com.musicloop.car.storage

import java.io.File
import java.nio.file.Files

data class EnumeratedMediaFile(
    val file: File,
    val relativePath: String,
    val fileName: String,
    val extension: String,
    val mediaType: MediaKind,
    val sizeBytes: Long,
    val modifiedTime: Long
)

data class EnumerateResult(
    val files: List<EnumeratedMediaFile>,
    val cancelled: Boolean = false,
    val rootVanished: Boolean = false
)

/**
 * Read-only media walk shared by the Phase 1 PoC scanner and the Phase 2A library scanner.
 *
 * Skips hidden names, symlink children, forbidden roots, and unreadable directories.
 * Still lists a StorageManager root even when that root itself is a mount symlink.
 */
class MediaEnumerator(
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
    private val lastModified: (File) -> Long = { file ->
        try {
            file.lastModified()
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
    }
) {

    fun collect(
        root: File,
        maxDepth: Int,
        maxFiles: Int,
        isCancelled: () -> Boolean = { false }
    ): EnumerateResult {
        val rootPath = filePath(root)
        if (ScanPolicy.isForbiddenScanRoot(rootPath)) {
            return EnumerateResult(files = emptyList())
        }
        if (rootVanished(root)) {
            return EnumerateResult(files = emptyList(), rootVanished = true)
        }
        val collected = mutableListOf<EnumeratedMediaFile>()
        var cancelled = false
        var vanished = false

        fun walk(dir: File, depth: Int) {
            if (cancelled || vanished) {
                return
            }
            if (isCancelled()) {
                cancelled = true
                return
            }
            if (collected.size >= maxFiles) {
                return
            }
            if (depth > maxDepth) {
                return
            }
            val path = filePath(dir)
            if (ScanPolicy.isForbiddenScanRoot(path)) {
                return
            }
            if (rootVanished(root)) {
                vanished = true
                return
            }
            // Volume roots on CARFU are often mount symlinks. Skip symlink *children*
            // only; still list the StorageManager root.
            if (depth > 0 && isSymlink(dir)) {
                return
            }
            val children = listChildren(dir) ?: return
            for (child in children) {
                if (cancelled || vanished) {
                    return
                }
                if (isCancelled()) {
                    cancelled = true
                    return
                }
                if (collected.size >= maxFiles) {
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
                collected += EnumeratedMediaFile(
                    file = child,
                    relativePath = relativeToRoot(rootPath, filePath(child), name),
                    fileName = name,
                    extension = ext,
                    mediaType = kind,
                    sizeBytes = fileLength(child),
                    modifiedTime = lastModified(child)
                )
            }
        }

        walk(root, 0)
        return EnumerateResult(
            files = collected.toList(),
            cancelled = cancelled,
            rootVanished = vanished
        )
    }

    private fun rootVanished(root: File): Boolean {
        return try {
            !root.exists() || !root.canRead()
        } catch (_: Exception) {
            true
        }
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

        fun relativeToRoot(rootAbsolute: String, fileAbsolute: String, fallbackName: String): String {
            val root = rootAbsolute.replace('\\', '/').trimEnd('/')
            val path = fileAbsolute.replace('\\', '/')
            if (path == root) {
                return fallbackName
            }
            val prefix = "$root/"
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length)
            }
            return fallbackName
        }
    }
}
