package com.musicloop.car.storage

import java.util.Locale

object CapabilityReportFormatter {

    fun format(device: DeviceInfo, volumes: List<VolumeReport>): String {
        val builder = StringBuilder()
        builder.appendLine("=== USB STORAGE REPORT ===")
        builder.append("Device: ")
        builder.append(device.brand)
        builder.append("/")
        builder.append(device.model)
        builder.append(" | Android SDK: ")
        builder.append(device.sdkInt)
        builder.append(" | Chip: ")
        builder.append(device.hardware.ifBlank { "unknown" })
        builder.appendLine()
        builder.appendLine()
        builder.append("Total Volumes Found: ")
        builder.append(volumes.size)
        builder.appendLine()
        if (volumes.isEmpty()) {
            builder.appendLine()
            builder.appendLine("No StorageVolume entries were returned by StorageManager.")
            return builder.toString().trimEnd()
        }
        volumes.forEach { volume ->
            builder.appendLine()
            builder.appendLine("========================================")
            appendVolume(builder, volume)
        }
        builder.appendLine("========================================")
        return builder.toString().trimEnd()
    }

    private fun appendVolume(builder: StringBuilder, volume: VolumeReport) {
        builder.append("VOLUME #")
        builder.append(volume.index)
        builder.appendLine()
        builder.append("Description: ")
        builder.append(volume.description)
        builder.appendLine()
        builder.append("State: ")
        builder.append(volume.state)
        builder.appendLine()
        builder.append("Removable Candidate: ")
        builder.append(volume.removableCandidate)
        builder.appendLine()
        builder.append("UUID: ")
        builder.append(volume.uuid ?: "N/A")
        builder.appendLine()
        builder.append("Root Path: ")
        builder.append(volume.rootPath ?: "N/A")
        builder.appendLine()
        builder.append("Exists: ")
        builder.append(volume.exists)
        builder.appendLine()
        builder.append("CanRead: ")
        builder.append(volume.canRead)
        builder.appendLine()
        builder.append("Total Space: ")
        builder.append(formatGb(volume.totalSpaceBytes))
        builder.append(" GB | Free Space: ")
        builder.append(formatGb(volume.freeSpaceBytes))
        builder.append(" GB")
        builder.appendLine()
        builder.appendLine()
        builder.appendLine("--- VERIFICATION CHECKS ---")
        builder.append("[1] Volume Detected: ")
        builder.append(passFail(volume.checks.volumeDetected))
        builder.appendLine()
        builder.append("[2] Root Resolved: ")
        builder.append(passFail(volume.checks.rootResolved))
        builder.appendLine()
        builder.append("[3] Directory Readable: ")
        builder.append(passFail(volume.checks.directoryReadable))
        builder.appendLine()
        builder.append("[4] Media Files Readable: ")
        builder.append(passFail(volume.checks.mediaFilesReadable))
        builder.appendLine()
        builder.appendLine()
        builder.appendLine("--- MEDIA SCAN RESULTS (Removable Only | Max Depth: 3, Limit: 50) ---")
        if (!volume.media.scanned) {
            builder.append("Scan skipped: ")
            builder.append(volume.media.skipReason ?: "n/a")
            builder.appendLine()
            return
        }
        builder.append("Found Audio Files: ")
        builder.append(volume.media.audioCount)
        builder.append(" ")
        builder.append(extensionBreakdown(MediaExtensions.AUDIO, volume.media.audioByExtension))
        builder.appendLine()
        builder.append("Found Video Files: ")
        builder.append(volume.media.videoCount)
        builder.append(" ")
        builder.append(extensionBreakdown(MediaExtensions.VIDEO, volume.media.videoByExtension))
        builder.appendLine()
        builder.appendLine()
        builder.appendLine("Sample Media File Read Test:")
        if (volume.media.samples.isEmpty()) {
            builder.appendLine("(no matching media files within scan limits)")
        } else {
            volume.media.samples.forEachIndexed { index, sample ->
                builder.append(index + 1)
                builder.append(". ")
                builder.append(sample.absolutePath)
                builder.append(" -> Stream Read: ")
                builder.append(passFail(sample.streamReadPass))
                builder.append(" (Size: ")
                builder.append(formatMb(sample.sizeBytes))
                builder.append(" MB)")
                builder.appendLine()
            }
        }
    }

    private fun extensionBreakdown(order: Set<String>, counts: Map<String, Int>): String {
        val parts = order.map { ext -> ".$ext: ${counts[ext] ?: 0}" }
        return "(${parts.joinToString(", ")})"
    }

    fun passFail(pass: Boolean): String = if (pass) "PASS" else "FAIL"

    fun formatGb(bytes: Long): String {
        if (bytes <= 0L) {
            return "0.00"
        }
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f", gb)
    }

    fun formatMb(bytes: Long): String {
        if (bytes <= 0L) {
            return "0.00"
        }
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f", mb)
    }
}
