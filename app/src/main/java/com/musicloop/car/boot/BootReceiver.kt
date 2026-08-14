package com.musicloop.car.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musicloop.car.player.PlaybackLog
import com.musicloop.car.playback.MusicPlaybackService
import com.musicloop.car.settings.AppSettingsStore

/**
 * Starts MusicPlaybackService after CARFU boot when Auto Start is enabled.
 * Does not crash if USB is missing. Does not auto-play by itself.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (!isBootAction(action)) {
            return
        }
        try {
            val settings = AppSettingsStore(context)
            if (!settings.autoStartService()) {
                PlaybackLog.i("BOOT auto-start disabled")
                return
            }
            PlaybackLog.i("BOOT starting MusicPlaybackService action=$action")
            MusicPlaybackService.start(context, fromBoot = true)
        } catch (error: Exception) {
            PlaybackLog.e("BOOT start failed", error)
        }
    }

    companion object {
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"

        fun isBootAction(action: String): Boolean {
            return action == ACTION_BOOT_COMPLETED ||
                action == ACTION_QUICKBOOT ||
                action == ACTION_HTC_QUICKBOOT
        }
    }
}
