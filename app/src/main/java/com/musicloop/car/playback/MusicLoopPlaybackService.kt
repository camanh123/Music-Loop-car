package com.musicloop.car.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicloop.car.MainActivity
import com.musicloop.car.MusicLoopApp
import com.musicloop.car.R

/**
 * Phase 2C.1 audio foreground session. Wraps the process-owned ExoPlayer already
 * created by [Media3PlayerManager]. Does not release that player (VideoActivity
 * may still be attached).
 *
 * Android 10 requires [Service.startForeground] shortly after
 * [ContextCompat.startForegroundService]. Media3 only posts its media notification
 * once playback is BUFFERING/READY, so this service enters foreground immediately
 * with a valid notification and then lets Media3 replace it.
 */
class MusicLoopPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var enteredForeground = false

    override fun onCreate() {
        super.onCreate()
        val app = application as MusicLoopApp
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // startForegroundService() requires a valid notification immediately on API 29.
        // Media3 only posts after BUFFERING/READY, so enter foreground first.
        enterForegroundNow(launch)
        val session = MediaSession.Builder(this, app.playerManager.sessionPlayer)
            .setId("musicloop-audio")
            .setSessionActivity(launch)
            .build()
        mediaSession = session
        addSession(session)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enteredForeground) {
            enterForegroundNow(mediaSession?.sessionActivity)
        }
        super.onStartCommand(intent, flags, startId)
        return Service.START_STICKY
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
        val session = mediaSession
        mediaSession = null
        if (session != null) {
            try {
                removeSession(session)
            } catch (_: Exception) {
                // Session may already be detached.
            }
            session.release()
        }
        // Never player.release() — VideoActivity still uses the process-owned ExoPlayer.
        super.onDestroy()
    }

    private fun enterForegroundNow(contentIntent: PendingIntent?) {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playback_notification_placeholder))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        startForegroundWithType(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, notification)
        enteredForeground = true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
            getString(R.string.playback_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundWithType(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(id, notification)
        }
    }

    companion object {
        fun ensureStarted(context: android.content.Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, MusicLoopPlaybackService::class.java)
            try {
                ContextCompat.startForegroundService(appContext, intent)
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
    }
}
