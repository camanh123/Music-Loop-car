package com.musicloop.car.usb

import android.util.Log
import com.musicloop.car.storage.VolumeSnapshot

/**
 * Structured USB diagnostics. Runtime [VolumeSnapshot.rootPath] may be logged
 * as observed by StorageManager; this class never hardcodes mount names.
 */
object UsbDiagnostics {
    const val TAG = "MusicLoopUSB"

    fun event(message: String) {
        Log.i(TAG, message)
    }

    fun volumes(action: String, snapshots: List<VolumeSnapshot>) {
        val removable = snapshots.filter { it.presentMountedRemovable }
        val summary = removable.joinToString(";") { snap ->
            "volumeId=${snap.volumeId},state=${snap.state},root=${snap.rootPath ?: "-"},scannable=${snap.scannable}"
        }.ifBlank { "-" }
        event(
            "USB_EVENT action=$action volumes=${snapshots.size} removableMounted=${removable.size} list=$summary"
        )
        val access = removable.joinToString(";") { snap ->
            "volumeId=${snap.volumeId},present=${snap.presentMountedRemovable},canRead=${snap.canRead},listFiles=${snap.listFilesNonNull},scannable=${snap.scannable}"
        }.ifBlank { "-" }
        com.musicloop.car.library.LibraryDiagnostics.log(
            "snapshot action=$action removableMounted=${removable.size} access=$access"
        )
    }
}
