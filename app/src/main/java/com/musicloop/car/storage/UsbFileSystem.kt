package com.musicloop.car.storage

import java.io.File

interface UsbFileSystem {
    fun exists(absolutePath: String): Boolean
    fun isRegularFile(absolutePath: String): Boolean
    fun canWrite(absolutePath: String): Boolean
    fun canonicalPath(absolutePath: String): String?
    fun deleteRegularFile(absolutePath: String): Boolean
}

class JavaUsbFileSystem : UsbFileSystem {
    override fun exists(absolutePath: String): Boolean {
        return flag { File(absolutePath).exists() }
    }

    override fun isRegularFile(absolutePath: String): Boolean {
        return flag { File(absolutePath).isFile }
    }

    override fun canWrite(absolutePath: String): Boolean {
        return flag { File(absolutePath).canWrite() }
    }

    override fun canonicalPath(absolutePath: String): String? {
        return try {
            File(absolutePath).canonicalPath
        } catch (_: Exception) {
            null
        }
    }

    override fun deleteRegularFile(absolutePath: String): Boolean {
        return UsbFileDeleteOp.deleteRegularFile(File(absolutePath))
    }

    private fun flag(block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (_: Exception) {
            false
        }
    }
}
