package com.musicloop.car.player

import android.media.MediaPlayer

object PlaybackErrors {
    fun fromMediaPlayer(what: Int, extra: Int): PlaybackError {
        val kind = when (what) {
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> PlaybackErrorKind.SERVER_DIED
            else -> when (extra) {
                MediaPlayer.MEDIA_ERROR_IO -> PlaybackErrorKind.IO
                MediaPlayer.MEDIA_ERROR_MALFORMED -> PlaybackErrorKind.MALFORMED
                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> PlaybackErrorKind.UNSUPPORTED
                MediaPlayer.MEDIA_ERROR_TIMED_OUT -> PlaybackErrorKind.TIMED_OUT
                else -> PlaybackErrorKind.UNKNOWN
            }
        }
        return PlaybackError(kind = kind, what = what, extra = extra)
    }

    fun prepareFailed(detail: String?): PlaybackError {
        return PlaybackError(
            kind = PlaybackErrorKind.PREPARE_FAILED,
            detail = detail
        )
    }

    fun missingFile(): PlaybackError {
        return PlaybackError(kind = PlaybackErrorKind.MISSING_FILE)
    }
}
