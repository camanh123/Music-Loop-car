package com.musicloop.car.storage

import java.io.File

/**
 * Sole production call site for java.io.File.delete().
 *
 * Callers must already prove: current-volume identity, USB-root containment,
 * regular supported media file, and writable capability. This object does not
 * re-interpret title, filename, list position, or lastKnownRootPath.
 */
internal object UsbFileDeleteOp {
    fun deleteRegularFile(file: File): Boolean {
        val isRegular = try {
            file.exists() && file.isFile
        } catch (_: Exception) {
            false
        }
        if (!isRegular) {
            return false
        }
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
