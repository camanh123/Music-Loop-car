package com.musicloop.car.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Listens for USB mount/unmount broadcasts. Discovery itself is read-only.
 */
class UsbMountMonitor(
    context: Context,
    private val onStorageChanged: () -> Unit
) {

    private val appContext = context.applicationContext
    private var registered = false

    private val fileSchemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in WATCHED_ACTIONS) {
                onStorageChanged()
            }
        }
    }

    private val plainReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in WATCHED_ACTIONS) {
                onStorageChanged()
            }
        }
    }

    fun start() {
        if (registered) {
            return
        }
        var any = false
        try {
            appContext.registerReceiver(fileSchemeReceiver, filterWithFileScheme())
            any = true
        } catch (_: Exception) {
            // Some firmwares reject file-scheme media filters.
        }
        try {
            appContext.registerReceiver(plainReceiver, filterWithoutScheme())
            any = true
        } catch (_: Exception) {
            // Some firmwares reject unschemed media filters.
        }
        registered = any
    }

    fun stop() {
        if (!registered) {
            return
        }
        try {
            appContext.unregisterReceiver(fileSchemeReceiver)
        } catch (_: Exception) {
            // Already unregistered.
        }
        try {
            appContext.unregisterReceiver(plainReceiver)
        } catch (_: Exception) {
            // Already unregistered.
        }
        registered = false
    }

    private fun filterWithFileScheme(): IntentFilter {
        return IntentFilter().apply {
            WATCHED_ACTIONS.forEach { addAction(it) }
            addDataScheme("file")
        }
    }

    private fun filterWithoutScheme(): IntentFilter {
        return IntentFilter().apply {
            WATCHED_ACTIONS.forEach { addAction(it) }
        }
    }

    companion object {
        private val WATCHED_ACTIONS = setOf(
            Intent.ACTION_MEDIA_MOUNTED,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_REMOVED,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_CHECKING
        )
    }
}
