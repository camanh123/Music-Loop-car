package com.musicloop.car.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsbScanWriteIsolationTest {

    @Test
    fun canWriteFalseStillScannableAndScanAllowed() {
        val snapshot = readableSnapshot().copy(canWrite = false)
        assertTrue(snapshot.presentMountedRemovable)
        assertTrue(snapshot.scannable)
        assertTrue(VolumeEligibility.isScannable(snapshot))
        val observed = UsbAccess.classify(snapshot)
        assertTrue(observed.readable)
        assertFalse(observed.allowsDelete)
    }

    @Test
    fun writeCapabilityCheckerThrowDoesNotAffectReadScan() {
        val snapshot = readableSnapshot().copy(canWrite = false)
        assertTrue(snapshot.scannable)
        val access = UsbAccess.classifyForDelete(snapshot) {
            throw IllegalStateException("write probe failed")
        }
        assertTrue(snapshot.scannable)
        assertTrue(snapshot.presentMountedRemovable)
        assertTrue(UsbAccess.classify(snapshot).readable)
        assertFalse(access.allowsDelete)
    }

    @Test
    fun snapshotAndScanSourcesMustNotProbeCanWrite() {
        val srcRoot = sourceRoot()
        val scanPathFiles = listOf(
            "storage/UsbStorageManager.kt",
            "storage/VolumeEligibility.kt",
            "usb/UsbLifecycleController.kt",
            "library/LibraryMediaScanner.kt",
            "storage/RecursiveMediaScanner.kt"
        )
        val hits = mutableListOf<String>()
        for (relative in scanPathFiles) {
            val file = File(srcRoot, "com/musicloop/car/$relative")
            assertTrue("Missing $relative", file.isFile)
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                if (line.contains(".canWrite(") || line.contains("canWrite()")) {
                    hits += "${file.name}:${index + 1}: $line"
                }
            }
        }
        assertTrue("Scan path probed canWrite:\n${hits.joinToString("\n")}", hits.isEmpty())
    }

    @Test
    fun deleteFlowIsTheOnlyWriteCapabilityCheck() {
        val srcRoot = sourceRoot()
        val allowed = setOf(
            "UsbFileSystem.kt",
            "UsbAccessCapability.kt",
            "SafeUsbDeletionCoordinator.kt",
            "MainActivity.kt"
        )
        val hits = mutableListOf<String>()
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }
                    if (line.contains(".canWrite(") || line.contains("canWrite()")) {
                        if (file.name !in allowed) {
                            hits += "${file.path}:${index + 1}: $line"
                        }
                    }
                }
            }
        assertTrue("Write probe escaped delete flow:\n${hits.joinToString("\n")}", hits.isEmpty())
    }

    private fun readableSnapshot(): VolumeSnapshot {
        return VolumeSnapshot(
            description = "USB DISK",
            state = "mounted",
            removable = true,
            isPrimary = false,
            uuid = "AAAA-AAAA",
            rootPath = "/mnt/media_rw/AAAA-AAAA",
            exists = true,
            isDirectory = true,
            canRead = true,
            listFilesNonNull = true,
            canWrite = false
        )
    }

    private fun sourceRoot(): File {
        val roots = listOf(
            File("src/main/java"),
            File("../app/src/main/java")
        )
        return roots.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("Could not locate production source root")
    }
}
