package com.musicloop.car.storage

import java.io.File
import java.io.FileInputStream

/**
 * Read-only I/O probe. Opens FileInputStream, reads a few bytes, closes immediately.
 * Never writes, truncates, or creates files.
 */
object StreamReadTester {
    fun canReadBytes(file: File, probeBytes: Int = ScanPolicy.STREAM_PROBE_BYTES): Boolean {
        return try {
            FileInputStream(file).use { stream ->
                val size = probeBytes.coerceAtLeast(1)
                val buffer = ByteArray(size)
                stream.read(buffer) > 0
            }
        } catch (_: Exception) {
            false
        }
    }
}
