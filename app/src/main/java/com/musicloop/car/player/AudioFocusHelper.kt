package com.musicloop.car.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Basic Android 10 audio focus. Fail-open on CARFU if the APIs misbehave:
 * playback still starts, and the app never crashes.
 *
 * Does not control FM radio, CANBUS, MCU, or vehicle volume hardware.
 */
class AudioFocusHelper(
    context: Context,
    private val onFocusLost: () -> Unit
) : AudioFocusGate {

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var request: AudioFocusRequest? = null
    private var holdingFocus: Boolean = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                PlaybackLog.i("AUDIO_FOCUS lost change=$change")
                holdingFocus = false
                onFocusLost()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                PlaybackLog.i("AUDIO_FOCUS gained; Phase 4 does not auto-resume")
            }
        }
    }

    override fun requestFocus(): Boolean {
        val manager = audioManager
        if (manager == null) {
            PlaybackLog.w("AUDIO_FOCUS AudioManager missing; playing without focus")
            return true
        }
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .build()
            request = focusRequest
            val result = manager.requestAudioFocus(focusRequest)
            holdingFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!holdingFocus) {
                PlaybackLog.w("AUDIO_FOCUS not granted result=$result; playing anyway")
            }
            true
        } catch (error: Exception) {
            PlaybackLog.w("AUDIO_FOCUS request failed: ${error.message}")
            true
        }
    }

    override fun abandonFocus() {
        val manager = audioManager ?: return
        val focusRequest = request
        holdingFocus = false
        if (focusRequest == null) {
            return
        }
        try {
            manager.abandonAudioFocusRequest(focusRequest)
        } catch (error: Exception) {
            PlaybackLog.w("AUDIO_FOCUS abandon failed: ${error.message}")
        }
        request = null
    }
}
