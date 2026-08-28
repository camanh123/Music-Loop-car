package com.musicloop.car.storage

/**
 * Chooses a live USB root from StorageVolume-derived path aliases.
 *
 * CARFU/Android often expose the same volume as both a public `/storage/<uuid>`
 * path and an internal `/mnt/...` alias. The first non-blank hidden-API path is
 * not always listable. This resolver never invents USB1/USB2 names.
 */
object UsbRootResolver {
    data class Probe(
        val exists: Boolean = false,
        val isDirectory: Boolean = false,
        val listFilesNonNull: Boolean = false
    )

    fun uniqueCandidates(raw: List<String?>): List<String> {
        val seen = LinkedHashSet<String>()
        for (value in raw) {
            val path = value?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (ScanPolicy.isForbiddenScanRoot(path)) {
                continue
            }
            seen += path
        }
        return seen.toList()
    }

    fun storageUuidCandidate(uuid: String?): String? {
        val id = uuid?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = "/storage/$id"
        if (ScanPolicy.isForbiddenScanRoot(path)) {
            return null
        }
        return path
    }

    /**
     * Prefer a directory that [File.listFiles] can actually open. Fall back to an
     * existing directory so the volume can still show as present.
     */
    fun selectRoot(
        candidates: List<String>,
        probe: (String) -> Probe
    ): String? {
        if (candidates.isEmpty()) {
            return null
        }
        val listable = candidates.firstOrNull { path ->
            val result = try {
                probe(path)
            } catch (_: Exception) {
                return@firstOrNull false
            }
            result.exists && result.isDirectory && result.listFilesNonNull
        }
        if (listable != null) {
            return listable
        }
        val existing = candidates.firstOrNull { path ->
            val result = try {
                probe(path)
            } catch (_: Exception) {
                return@firstOrNull false
            }
            result.exists && result.isDirectory
        }
        return existing ?: candidates.first()
    }
}
