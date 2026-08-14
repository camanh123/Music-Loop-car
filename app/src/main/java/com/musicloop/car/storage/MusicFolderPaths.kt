package com.musicloop.car.storage

/**
 * Pure path helpers for USB music-folder identity.
 * Never creates, writes, or modifies files.
 */
object MusicFolderPaths {

    private val fatUuid = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")

    fun normalizeAbsolute(path: String): String {
        val replaced = path.replace('\\', '/').trim()
        if (replaced == "/") return replaced
        return replaced.trimEnd('/')
    }

    fun normalizeRelative(path: String): String {
        return path.replace('\\', '/')
            .trim()
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
            .joinToString("/")
    }

    fun folderName(relativePath: String): String {
        val normalized = normalizeRelative(relativePath)
        return normalized.substringAfterLast('/', normalized).ifEmpty { normalized }
    }

    fun volumeIdentityFromPath(volumeRoot: String): String? {
        val name = normalizeAbsolute(volumeRoot).substringAfterLast('/')
        return name.takeIf { fatUuid.matches(it) }
    }

    fun relativeToVolume(volumeRoot: String, absoluteFolder: String): String? {
        val root = normalizeAbsolute(volumeRoot)
        val folder = normalizeAbsolute(absoluteFolder)
        if (folder == root) {
            return ""
        }
        val prefix = "$root/"
        if (!folder.startsWith(prefix)) {
            return null
        }
        return normalizeRelative(folder.removePrefix(prefix))
    }

    fun join(volumeRoot: String, relativePath: String): String {
        val root = normalizeAbsolute(volumeRoot)
        val relative = normalizeRelative(relativePath)
        return if (relative.isEmpty()) root else "$root/$relative"
    }

    fun isSamePath(a: String, b: String): Boolean {
        return normalizeAbsolute(a).equals(normalizeAbsolute(b), ignoreCase = true)
    }

    fun isSameOrChildPath(child: String, parent: String): Boolean {
        val c = normalizeAbsolute(child)
        val p = normalizeAbsolute(parent)
        if (c.equals(p, ignoreCase = true)) {
            return true
        }
        return c.startsWith("$p/", ignoreCase = true)
    }

    fun displayMusicFolder(volumeLabel: String?, relativePath: String): String {
        val volume = volumeLabel?.takeIf { it.isNotBlank() } ?: "USB"
        val shortVolume = if (volume.contains("USB", ignoreCase = true)) "USB" else volume
        val relative = normalizeRelative(relativePath)
        return if (relative.isEmpty()) {
            shortVolume
        } else {
            "$shortVolume / ${relative.replace("/", " / ")}"
        }
    }
}
