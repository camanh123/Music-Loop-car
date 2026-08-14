package com.musicloop.car.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.musicloop.car.MainActivity
import com.musicloop.car.R
import com.musicloop.car.player.PlaybackSnapshot
import com.musicloop.car.player.PlayerState

object PlaybackNotification {
    const val CHANNEL_ID = "musicloop_playback"
    const val NOTIFICATION_ID = 41

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = context.getString(R.string.notification_channel)
        channel.setSound(null, null)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, snapshot: PlaybackSnapshot): Notification {
        ensureChannel(context)
        val track = snapshot.track
        val title = track?.title?.ifBlank { track.filename } ?: context.getString(R.string.app_name)
        val text = when (snapshot.state) {
            PlayerState.PLAYING -> context.getString(R.string.notification_playing)
            PlayerState.PAUSED -> context.getString(R.string.notification_paused)
            PlayerState.PREPARING -> context.getString(R.string.playback_preparing)
            PlayerState.USB_DISCONNECTED -> context.getString(R.string.usb_status_disconnected)
            PlayerState.ERROR -> context.getString(R.string.playback_error_unplayable)
            PlayerState.COMPLETED -> context.getString(R.string.notification_paused)
            PlayerState.IDLE -> snapshot.track?.artist?.ifBlank { context.getString(R.string.notification_ready) }
                ?: context.getString(R.string.notification_ready)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags()
        )
        val playLabel = if (snapshot.state == PlayerState.PLAYING) {
            context.getString(R.string.action_pause)
        } else {
            context.getString(R.string.action_play_pause)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(snapshot.state == PlayerState.PLAYING || snapshot.state == PlayerState.PREPARING)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                0,
                context.getString(R.string.action_previous),
                servicePending(context, MusicPlaybackService.ACTION_PREVIOUS, 1)
            )
            .addAction(
                0,
                playLabel,
                servicePending(context, MusicPlaybackService.ACTION_PLAY_PAUSE, 2)
            )
            .addAction(
                0,
                context.getString(R.string.action_next),
                servicePending(context, MusicPlaybackService.ACTION_NEXT, 3)
            )
            .build()
    }

    private fun servicePending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(context, requestCode, intent, pendingFlags())
    }

    private fun pendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
