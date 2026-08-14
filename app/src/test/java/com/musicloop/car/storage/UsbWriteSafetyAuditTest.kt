package com.musicloop.car.storage

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsbWriteSafetyAuditTest {

    private val forbidden = listOf(
        "FileOutputStream",
        "FileWriter",
        "RandomAccessFile",
        "renameTo(",
        ".delete(",
        "deleteRecursively",
        "Files.copy",
        "Files.move",
        "File.copyTo",
        "File.copyRecursively",
        "mkdir(",
        "mkdirs(",
        "createNewFile",
        "DocumentFile.createFile",
        "DocumentFile.delete",
        "DocumentFile.renameTo",
        "openFileOutput("
    )

    @Test
    fun productionSourcesMustNotContainUsbWriteApis() {
        val roots = listOf(
            File("src/main/java"),
            File("../app/src/main/java")
        )
        val srcRoot = roots.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("Could not locate production source root")

        val hits = mutableListOf<String>()
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }
                    forbidden.forEach { token ->
                        if (line.contains(token)) {
                            hits += "${file.path}:${index + 1}: $token"
                        }
                    }
                }
            }

        assertTrue("USB write APIs found:\n${hits.joinToString("\n")}", hits.isEmpty())

        val manifestCandidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        val manifest = manifestCandidates.first { it.isFile }.readText()
        assertTrue(
            "INTERNET permission must not be requested",
            !manifest.contains("android.permission.INTERNET")
        )
        assertTrue(
            "WRITE_EXTERNAL_STORAGE must not be requested",
            !manifest.contains("WRITE_EXTERNAL_STORAGE")
        )
    }
}
