package com.musicloop.car.storage

object ScanPolicy {
    const val MAX_DEPTH = 3
    const val MAX_FILES = 50
    const val STREAM_PROBE_BYTES = 16
    const val SAMPLE_LIMIT = 10

    fun isForbiddenScanRoot(absolutePath: String): Boolean {
        val normalized = absolutePath.replace('\\', '/').lowercase().trimEnd('/')
        if (normalized.isEmpty() || normalized == "/") {
            return true
        }
        return normalized == "/data" ||
            normalized.startsWith("/data/") ||
            normalized == "/system" ||
            normalized.startsWith("/system/") ||
            normalized == "/proc" ||
            normalized.startsWith("/proc/") ||
            normalized == "/dev" ||
            normalized.startsWith("/dev/") ||
            normalized.contains("/emulated/")
    }

    fun isHiddenName(name: String): Boolean {
        return name.startsWith('.') && name != "." && name != ".."
    }
}
