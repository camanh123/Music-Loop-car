package com.musicloop.car.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicloop.car.MainActivity
import com.musicloop.car.MusicLoopApp

/**
 * Phase 2C.1 audio foreground session. Wraps the process-owned ExoPlayer already
 * created by [Media3PlayerManager]. Does not release that player (VideoActivity
 * may still be attached).
 */
class MusicLoopPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MusicLoopApp
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            pendingIntentFlags()
        )
        mediaSession = MediaSession.Builder(this, app.playerManager.sessionPlayer)
            .setId("musicloop-audio")
            .setSessionActivity(launch)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val state = (application as MusicLoopApp).playerManager.state.value
        if (!BackgroundPlaybackPolicy.shouldHoldService(state)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        fun ensureStarted(context: android.content.Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, MusicLoopPlaybackService::class.java)
            try {
                androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Exception) {
                try {
                    appContext.startService(intent)
                } catch (_: Exception) {
                    // Playback must continue in-process even if the session service cannot start.
                }
            }
        }

        fun stop(context: android.content.Context) {
            try {
                val appContext = context.applicationContext
                appContext.stopService(Intent(appContext, MusicLoopPlaybackService::class.java))
            } catch (_: Exception) {
                // Stopping the session must not crash USB cleanup.
            }
        }

        private fun pendingIntentFlags(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
    }
}
