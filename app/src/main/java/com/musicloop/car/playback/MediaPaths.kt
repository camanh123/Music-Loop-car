package com.musicloop.car.playback

/**
 * Runtime path joining. Root is never a stored identity and must not be hardcoded.
 */
object MediaPaths {
    fun join(rootPath: String, relativePath: String): String? {
        val root = normalize(rootPath) ?: return null
        val relative = relativePath.replace('\\', '/').trimStart('/')
        if (root.isBlank() || relative.isBlank()) {
            return null
        }
        if (hasTraversal(relative)) {
            return null
        }
        return "$root/$relative"
    }

    /**
     * True only when [absolutePath] is a file strictly inside [rootPath].
     * Prefix matches without a directory boundary (e.g. /mnt/usb vs /mnt/usb2) fail.
     */
    fun isContained(rootPath: String, absolutePath: String): Boolean {
        val root = normalize(rootPath) ?: return false
        val absolute = normalize(absolutePath) ?: return false
        if (hasTraversal(rootPath) || hasTraversal(absolutePath)) {
            return false
        }
        val prefix = "$root/"
        return absolute.startsWith(prefix) && absolute.length > prefix.length
    }

    fun normalize(path: String): String? {
        val trimmed = path.replace('\\', '/').trim()
        if (trimmed.isBlank()) {
            return null
        }
        if (hasTraversal(trimmed)) {
            return null
        }
        val collapsed = trimmed.replace(Regex("/+"), "/")
        return if (collapsed.length > 1) collapsed.trimEnd('/') else collapsed
    }

    fun hasTraversal(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized == ".." ||
            normalized.startsWith("../") ||
            normalized.contains("/../") ||
            normalized.endsWith("/..") ||
            normalized.split('/').any { it == ".." }
    }
}
