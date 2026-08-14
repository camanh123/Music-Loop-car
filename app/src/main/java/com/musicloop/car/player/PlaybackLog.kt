package com.musicloop.car.player

import android.util.Log

/**
 * Logcat-only CARFU playback diagnostics. Never writes files to USB.
 *
 * REAL CARFU HARDWARE TEST REQUIRED for codec/container claims.
 * Unit tests do not prove MediaPlayer compatibility on UIS7862.
 */
object PlaybackLog {
    const val TAG = "MusicLoopCar"

    fun i(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Exception) {
            // Some head units reject unexpected log tags; never crash playback.
        }
    }

    fun w(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Exception) {
        }
    }

    fun e(message: String, error: Throwable? = null) {
        try {
            if (error != null) {
                Log.e(TAG, message, error)
            } else {
                Log.e(TAG, message)
            }
        } catch (_: Exception) {
        }
    }

    fun prepareAttempt(filename: String, extension: String, absolutePath: String) {
        i("PLAY_PREPARE filename=$filename ext=$extension path=$absolutePath")
    }

    fun prepared(filename: String, extension: String, durationMs: Int) {
        i("PLAY_PREPARED filename=$filename ext=$extension durationMs=$durationMs result=OK")
    }

    fun playbackStarted(filename: String, extension: String) {
        i("PLAY_STARTED filename=$filename ext=$extension result=OK")
    }

    fun playbackError(
        filename: String,
        extension: String,
        error: PlaybackError
    ) {
        e(
            "PLAY_ERROR filename=$filename ext=$extension " +
                "kind=${error.kind} what=${error.what} extra=${error.extra} " +
                "detail=${error.detail ?: "-"}"
        )
    }

    fun usbDisconnected(filename: String?) {
        w("USB_DISCONNECTED during playback track=${filename ?: "-"}")
    }
}
