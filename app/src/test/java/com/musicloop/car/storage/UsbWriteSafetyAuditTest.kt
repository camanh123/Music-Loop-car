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
        "openFileOutput(",
        "ACTION_OPEN_DOCUMENT_TREE",
        "ACTION_OPEN_DOCUMENT",
        "DocumentsContract",
        "androidx.media3",
        "com.google.android.exoplayer2",
        "android.media.MediaPlayer",
        "MediaSessionService",
        "androidx.media.session"
    )

    private val forbiddenPaths = listOf(
        "/storage/USB1",
        "/storage/USB2"
    )

    @Test
    fun productionSourcesMustNotContainUsbWriteApisOrHardcodedUsbPaths() {
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
                    forbiddenPaths.forEach { token ->
                        if (line.contains(token)) {
                            hits += "${file.path}:${index + 1}: $token"
                        }
                    }
                }
            }

        assertTrue("Forbidden USB write/SAF/hardcoded paths:\n${hits.joinToString("\n")}", hits.isEmpty())

        val manifestCandidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        val manifest = manifestCandidates.first { it.isFile }.readText()
        assertTrue(
            "INTERNET permission must not be requested",
            !Regex("""<uses-permission[^>]*android\.permission\.INTERNET""").containsMatchIn(manifest)
        )
        assertTrue(
            "WRITE_EXTERNAL_STORAGE must not be requested",
            !Regex("""<uses-permission[^>]*WRITE_EXTERNAL_STORAGE""").containsMatchIn(manifest)
        )
        assertTrue(
            "FOREGROUND_SERVICE must not be requested in Phase 2A",
            !Regex("""<uses-permission[^>]*FOREGROUND_SERVICE""").containsMatchIn(manifest)
        )
        assertTrue(
            "SAF tree picker must not be declared",
            !manifest.contains("OPEN_DOCUMENT_TREE")
        )
    }
}
