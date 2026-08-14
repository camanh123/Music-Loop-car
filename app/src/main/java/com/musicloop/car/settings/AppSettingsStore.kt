package com.musicloop.car.settings

import android.content.Context

/**
 * Internal app settings. Never written to USB.
 *
 * Auto-start service (default ON) and auto-play (default OFF) are independent.
 */
class AppSettingsStore(context: Context) : AppSettingsRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun autoStartService(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START, true)
    }

    override fun setAutoStartService(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    override fun autoPlayOnBoot(): Boolean {
        return prefs.getBoolean(KEY_AUTO_PLAY, false)
    }

    override fun setAutoPlayOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY, enabled).apply()
    }

    companion object {
        const val PREFS_NAME = "app_settings"
        private const val KEY_AUTO_START = "auto_start_service"
        private const val KEY_AUTO_PLAY = "auto_play_on_boot"
    }
}

interface AppSettingsRepository {
    fun autoStartService(): Boolean
    fun setAutoStartService(enabled: Boolean)
    fun autoPlayOnBoot(): Boolean
    fun setAutoPlayOnBoot(enabled: Boolean)
}

class InMemoryAppSettings(
    var autoStart: Boolean = true,
    var autoPlay: Boolean = false
) : AppSettingsRepository {
    override fun autoStartService(): Boolean = autoStart
    override fun setAutoStartService(enabled: Boolean) {
        autoStart = enabled
    }
    override fun autoPlayOnBoot(): Boolean = autoPlay
    override fun setAutoPlayOnBoot(enabled: Boolean) {
        autoPlay = enabled
    }
}
