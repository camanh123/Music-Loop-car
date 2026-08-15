package com.musicloop.car.playback

/**
 * Runtime path joining. Root is never a stored identity and must not be hardcoded.
 */
object MediaPaths {
    fun join(rootPath: String, relativePath: String): String? {
        val root = rootPath.replace('\\', '/').trimEnd('/')
        val relative = relativePath.replace('\\', '/').trimStart('/')
        if (root.isBlank() || relative.isBlank()) {
            return null
        }
        if (relative == ".." || relative.startsWith("../") || relative.contains("/../")) {
            return null
        }
        return "$root/$relative"
    }
}
