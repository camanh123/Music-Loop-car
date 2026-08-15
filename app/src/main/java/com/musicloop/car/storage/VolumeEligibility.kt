package com.musicloop.car.storage

import android.os.Environment

/**
 * Pure rules for whether a StorageVolume snapshot may be scanned.
 * BroadcastReceiver events are not the source of truth — these checks are.
 */
object VolumeEligibility {
    fun isMounted(state: String): Boolean {
        return state == Environment.MEDIA_MOUNTED ||
            state == Environment.MEDIA_MOUNTED_READ_ONLY ||
            state.equals("mounted", ignoreCase = true) ||
            state.equals("mounted_ro", ignoreCase = true)
    }

    fun isPresentMountedRemovable(snapshot: VolumeSnapshot): Boolean {
        if (!snapshot.removable || !isMounted(snapshot.state) || snapshot.isPrimary) {
            return false
        }
        val root = snapshot.rootPath
        if (!root.isNullOrBlank() && ScanPolicy.isForbiddenScanRoot(root)) {
            return false
        }
        return true
    }

    fun isScannable(snapshot: VolumeSnapshot): Boolean {
        val root = snapshot.rootPath
        return snapshot.removable &&
            isMounted(snapshot.state) &&
            !snapshot.isPrimary &&
            !root.isNullOrBlank() &&
            snapshot.exists &&
            snapshot.isDirectory &&
            snapshot.canRead &&
            snapshot.listFilesNonNull &&
            !ScanPolicy.isForbiddenScanRoot(root)
    }

    fun skipReason(snapshot: VolumeSnapshot): String {
        val root = snapshot.rootPath
        return when {
            !snapshot.removable || snapshot.isPrimary -> "not removable"
            !isMounted(snapshot.state) -> "not mounted"
            root.isNullOrBlank() -> "root unresolved"
            ScanPolicy.isForbiddenScanRoot(root) -> "internal/forbidden"
            !snapshot.exists || !snapshot.isDirectory || !snapshot.canRead || !snapshot.listFilesNonNull ->
                "directory not listable"
            else -> "not scannable"
        }
    }
}
