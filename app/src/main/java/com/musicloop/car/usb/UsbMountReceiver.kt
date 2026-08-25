package com.musicloop.car.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musicloop.car.MusicLoopApp

/**
 * Trigger only. StorageManager verification happens in [UsbLifecycleController].
 * The broadcast data URI is not used as volume identity.
 */
class UsbMountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? MusicLoopApp ?: return
        val action = intent?.action
        UsbDiagnostics.event("USB_EVENT action=$action source=BroadcastReceiver")
        app.lifecycleController.onBroadcast(action)
    }
}
