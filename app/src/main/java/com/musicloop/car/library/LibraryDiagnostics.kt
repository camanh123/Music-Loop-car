package com.musicloop.car.library

import android.util.Log
import com.musicloop.car.storage.VolumeSnapshot

/**
 * Temporary CARFU investigation logs for the 2D.5 empty-library regression.
 * Observational only. Does not delete, scan, or mutate Room.
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
                "state=${snapshot.state} online=${snapshot.presentMountedRemovable} " +
                "readable=${snapshot.canRead} writable=${snapshot.canWrite} " +
                "listFiles=${snapshot.listFilesNonNull} scannable=${snapshot.scannable}"
        )
    }
}
