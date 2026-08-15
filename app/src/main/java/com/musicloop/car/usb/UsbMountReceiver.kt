package com.musicloop.car.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musicloop.car.MusicLoopApp

/**
 * Trigger only. StorageManager verification happens in [UsbLifecycleController].
 */
class UsbMountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? MusicLoopApp ?: return
        app.lifecycleController.onBroadcast(intent?.action)
    }
}
