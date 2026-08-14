package com.musicloop.car.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Optional DocumentsUI fallback. Never the primary picker.
 * Must not crash if the system picker is missing.
 */
object SafTreeFallback {

    fun createOpenTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun resolveTreeToFilesystemPath(context: Context, treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri) ?: return null
            val parts = docId.split(":", limit = 2)
            val volumeId = parts.getOrNull(0) ?: return null
            val relative = parts.getOrNull(1).orEmpty()
            val root = when {
                volumeId.equals("primary", ignoreCase = true) -> return null
                else -> findVolumeRoot(context, volumeId)
            } ?: return null
            val path = MusicFolderPaths.join(root, relative)
            val file = File(path)
            if (file.isDirectory) path else null
        } catch (_: Exception) {
            null
        }
    }

    private fun findVolumeRoot(context: Context, volumeId: String): String? {
        val volumes = UsbStorageManager(context).discoverMountedVolumes()
        val match = volumes.find { volume ->
            volume.uuid.equals(volumeId, ignoreCase = true) ||
                volume.identity.equals(volumeId, ignoreCase = true) ||
                MusicFolderPaths.normalizeAbsolute(volume.absolutePath)
                    .substringAfterLast('/')
                    .equals(volumeId, ignoreCase = true)
        }
        return match?.absolutePath
    }
}
