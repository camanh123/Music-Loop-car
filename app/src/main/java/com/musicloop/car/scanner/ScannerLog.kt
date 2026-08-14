package com.musicloop.car.scanner

import android.util.Log

/**
 * Logcat-only scanner diagnostics. Never writes files to USB.
 * Tag matches playback logs so CARFU captures one filter: MusicLoopCar.
 */
object ScannerLog {
    const val TAG = "MusicLoopCar"

    fun i(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Exception) {
            // Some head units reject unexpected log tags; never crash the scan.
        }
    }

    fun w(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Exception) {
        }
    }

    fun error(where: String, error: Throwable) {
        val klass = error.javaClass.name
        val message = error.message ?: "-"
        try {
            Log.e(TAG, "MusicLoopCar scanner error $where class=$klass message=$message")
        } catch (_: Exception) {
        }
    }

    fun error(where: String, className: String?, message: String?) {
        try {
            Log.e(
                TAG,
                "MusicLoopCar scanner error $where class=${className ?: "-"} message=${message ?: "-"}"
            )
        } catch (_: Exception) {
        }
    }
}
