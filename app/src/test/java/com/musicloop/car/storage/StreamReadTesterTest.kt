package com.musicloop.car.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class StreamReadTesterTest {
    @Test
    fun readsFirstBytesFromExistingFile() {
        val dir = createTempDirectory("usb-poc-read").toFile()
        try {
            val file = dir.resolve("clip.aac")
            file.writeText("abcdef")
            assertTrue(StreamReadTester.canReadBytes(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingFileFailsWithoutThrowing() {
        val missing = java.io.File("/tmp/musicloop-missing-stream-file-xyz.mp3")
        assertFalse(missing.exists())
        assertFalse(StreamReadTester.canReadBytes(missing))
    }
}
