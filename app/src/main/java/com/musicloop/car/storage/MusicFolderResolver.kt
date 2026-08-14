package com.musicloop.car.storage

sealed class FolderResolveResult {
    data class Found(
        val absolutePath: String,
        val volume: UsbVolume,
        val record: MusicFolderRecord
    ) : FolderResolveResult()

    object WaitingForUsb : FolderResolveResult()

    object FolderNotFound : FolderResolveResult()

    /**
     * Multiple volumes expose the same relative path and identity is insufficient.
     * Never auto-select an unrelated folder.
     */
    object Ambiguous : FolderResolveResult()
}

/**
 * Restores a remembered music folder onto currently mounted USB volumes.
 */
class MusicFolderResolver(
    private val isDirectory: (String) -> Boolean
) {

    fun resolve(saved: MusicFolderRecord?, volumes: List<UsbVolume>): FolderResolveResult {
        if (saved == null) {
            return if (volumes.isEmpty()) {
                FolderResolveResult.WaitingForUsb
            } else {
                FolderResolveResult.WaitingForUsb
            }
        }

        val mounted = volumes.filter { isDirectory(it.absolutePath) }
        if (mounted.isEmpty()) {
            return FolderResolveResult.WaitingForUsb
        }

        val savedIdentity = saved.volumeUuid?.takeIf { it.isNotBlank() }

        if (savedIdentity != null) {
            val matchedVolume = mounted.find { volume ->
                identitiesMatch(volume.identity, savedIdentity)
            }
            if (matchedVolume == null) {
                // A different stick may be inserted. Do not pick its folders.
                return FolderResolveResult.WaitingForUsb
            }
            val resolved = MusicFolderPaths.join(matchedVolume.absolutePath, saved.relativePath)
            return if (isDirectory(resolved)) {
                found(resolved, matchedVolume, saved)
            } else {
                FolderResolveResult.FolderNotFound
            }
        }

        val exactAbsolute = mounted.find { volume ->
            val resolved = MusicFolderPaths.join(volume.absolutePath, saved.relativePath)
            MusicFolderPaths.isSamePath(resolved, saved.absolutePath) && isDirectory(saved.absolutePath)
        }
        if (exactAbsolute != null && isDirectory(saved.absolutePath)) {
            return found(MusicFolderPaths.normalizeAbsolute(saved.absolutePath), exactAbsolute, saved)
        }

        if (isDirectory(saved.absolutePath)) {
            val parentVolume = mounted.find { volume ->
                MusicFolderPaths.isSameOrChildPath(saved.absolutePath, volume.absolutePath)
            }
            if (parentVolume != null) {
                return found(MusicFolderPaths.normalizeAbsolute(saved.absolutePath), parentVolume, saved)
            }
        }

        val relativeMatches = mounted.mapNotNull { volume ->
            val candidate = MusicFolderPaths.join(volume.absolutePath, saved.relativePath)
            if (isDirectory(candidate)) volume to candidate else null
        }

        return when (relativeMatches.size) {
            0 -> FolderResolveResult.FolderNotFound
            1 -> {
                val (volume, path) = relativeMatches.first()
                found(path, volume, saved)
            }
            else -> FolderResolveResult.Ambiguous
        }
    }

    private fun found(
        absolutePath: String,
        volume: UsbVolume,
        saved: MusicFolderRecord
    ): FolderResolveResult.Found {
        val relative = MusicFolderPaths.relativeToVolume(volume.absolutePath, absolutePath)
            ?: saved.relativePath
        val updated = saved.copy(
            absolutePath = MusicFolderPaths.normalizeAbsolute(absolutePath),
            relativePath = relative,
            volumeUuid = volume.identity ?: saved.volumeUuid,
            volumeLabel = volume.label ?: saved.volumeLabel,
            folderName = MusicFolderPaths.folderName(relative).ifEmpty {
                saved.folderName.ifEmpty { volume.label ?: "USB" }
            }
        )
        return FolderResolveResult.Found(updated.absolutePath, volume, updated)
    }

    private fun identitiesMatch(volumeIdentity: String?, savedIdentity: String): Boolean {
        if (volumeIdentity.isNullOrBlank()) {
            return false
        }
        return volumeIdentity.equals(savedIdentity, ignoreCase = true)
    }
}
