package com.musicloop.car.storage

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Thrown when code attempts to write outside internal app storage.
 */
class UsbWriteForbiddenException(message: String) : SecurityException(message)

/**
 * USB read-only policy for MusicLoop Car.
 *
 * Audio files on USB may only be opened for reading. Database, cache,
 * artwork, logs, and temp files must stay in internal app storage.
 * This class does not repair, format, or modify USB filesystems.
 */
object UsbWriteGuard {

    fun isAppInternalStorage(context: Context, path: File): Boolean {
        val canonical = path.canonicalOrAbsolute()
        return appWritableRoots(context).any { root -> canonical.isSameOrChildOf(root) }
    }

    fun assertCanWrite(context: Context, path: File) {
        if (!isAppInternalStorage(context, path)) {
            throw UsbWriteForbiddenException(
                "Refusing write outside internal app storage: ${path.absolutePath}"
            )
        }
    }

    fun openReadOnly(file: File): FileInputStream {
        return FileInputStream(file)
    }

    private fun appWritableRoots(context: Context): List<File> {
        return listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.codeCacheDir,
            context.noBackupFilesDir
        ).map { it.canonicalOrAbsolute() }
    }

    private fun File.canonicalOrAbsolute(): File {
        return try {
            canonicalFile
        } catch (_: IOException) {
            absoluteFile
        }
    }

    private fun File.isSameOrChildOf(parent: File): Boolean {
        if (this == parent) {
            return true
        }
        val parentPath = parent.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        return path.startsWith(parentPath)
    }
}
