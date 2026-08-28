package com.musicloop.car.library

import android.util.Log
import com.musicloop.car.storage.VolumeSnapshot

/**
 * Lightweight USB/library diagnostics. Observational only.
 * Does not probe write capability and does not log individual media files.
 */
object LibraryDiagnostics {
    const val TAG = "MusicLoopCar"

    fun log(message: String) {
        Log.i(TAG, message)
    }

    fun volume(snapshot: VolumeSnapshot?) {
        if (snapshot == null) {
            log("volume none")
            return
        }
        log(
            "volume volumeId=${snapshot.volumeId} root=${snapshot.rootPath ?: "-"} " +
                "state=${snapshot.state} present=${snapshot.presentMountedRemovable} " +
                "readable=${snapshot.canRead} listFiles=${snapshot.listFilesNonNull} " +
                "scannable=${snapshot.scannable}"
        )
    }
}
