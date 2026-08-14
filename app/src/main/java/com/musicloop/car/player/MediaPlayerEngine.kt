package com.musicloop.car.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * Single-instance Android 10 MediaPlayer wrapper.
 *
 * Lifecycle: Idle → setDataSource → prepareAsync → Prepared → start/pause/seek
 * → Completion or Error. Error and USB removal always reset+release.
 *
 * Does not write, convert, or modify the audio file. Data source is read-only.
 */
class MediaPlayerEngine(
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) : PlayerEngine {

    private var player: MediaPlayer? = null
    private var listener: PlayerEngineListener? = null
    private var preparing: Boolean = false
    private var released: Boolean = false

    override fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    override fun prepare(absolutePath: String) {
        released = false
        preparing = true
        val mp = obtainPlayer()
        try {
            mp.reset()
            attachListeners(mp)
            applyAudioAttributes(mp)
            mp.setDataSource(absolutePath)
            mp.prepareAsync()
        } catch (error: Exception) {
            preparing = false
            PlaybackLog.e("MediaPlayer prepare failed", error)
            releaseBroken()
            notifyError(PlaybackErrors.prepareFailed(error.message))
        }
    }

    override fun start() {
        val mp = player ?: return
        try {
            mp.start()
        } catch (error: Exception) {
            PlaybackLog.e("MediaPlayer start failed", error)
            releaseBroken()
            notifyError(PlaybackErrors.prepareFailed(error.message))
        }
    }

    override fun pause() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
            }
        } catch (error: Exception) {
            PlaybackLog.e("MediaPlayer pause failed", error)
        }
    }

    override fun seekTo(positionMs: Int) {
        val mp = player ?: return
        try {
            mp.seekTo(positionMs.coerceAtLeast(0))
        } catch (error: Exception) {
            PlaybackLog.e("MediaPlayer seekTo failed", error)
        }
    }

    override fun stop() {
        preparing = false
        val mp = player ?: return
        try {
            mp.stop()
        } catch (_: Exception) {
            // Already idle/error; reset below.
        }
        try {
            mp.reset()
        } catch (_: Exception) {
        }
    }

    override fun reset() {
        preparing = false
        val mp = player ?: return
        try {
            mp.reset()
            attachListeners(mp)
        } catch (error: Exception) {
            PlaybackLog.e("MediaPlayer reset failed", error)
            releaseBroken()
        }
    }

    override fun release() {
        preparing = false
        released = true
        val mp = player
        player = null
        if (mp == null) {
            return
        }
        try {
            mp.setOnPreparedListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.setOnInfoListener(null)
        } catch (_: Exception) {
        }
        try {
            mp.reset()
        } catch (_: Exception) {
        }
        try {
            mp.release()
        } catch (error: Exception) {
            PlaybackLog.e("MediaPlayer release failed", error)
        }
    }

    override fun currentPosition(): Int {
        val mp = player ?: return 0
        return try {
            mp.currentPosition
        } catch (_: Exception) {
            0
        }
    }

    override fun duration(): Int {
        val mp = player ?: return 0
        return try {
            mp.duration.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    override fun isPlaying(): Boolean {
        val mp = player ?: return false
        return try {
            mp.isPlaying
        } catch (_: Exception) {
            false
        }
    }

    private fun obtainPlayer(): MediaPlayer {
        val existing = player
        if (existing != null) {
            return existing
        }
        val created = MediaPlayer()
        applyAudioAttributes(created)
        attachListeners(created)
        player = created
        return created
    }

    private fun applyAudioAttributes(mp: MediaPlayer) {
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        } catch (error: Exception) {
            PlaybackLog.w("setAudioAttributes failed: ${error.message}")
        }
    }

    private fun attachListeners(mp: MediaPlayer) {
        mp.setOnPreparedListener { prepared ->
            if (released || prepared !== player) {
                return@setOnPreparedListener
            }
            preparing = false
            val duration = try {
                prepared.duration.coerceAtLeast(0)
            } catch (_: Exception) {
                0
            }
            postOnMain { listener?.onPrepared(duration) }
        }
        mp.setOnCompletionListener { completed ->
            if (released || completed !== player) {
                return@setOnCompletionListener
            }
            postOnMain { listener?.onCompletion() }
        }
        mp.setOnErrorListener { errored, what, extra ->
            if (released || errored !== player) {
                return@setOnErrorListener true
            }
            preparing = false
            val error = PlaybackErrors.fromMediaPlayer(what, extra)
            PlaybackLog.e("MediaPlayer onError what=$what extra=$extra kind=${error.kind}")
            releaseBroken()
            postOnMain { listener?.onError(error) }
            true
        }
        mp.setOnInfoListener { _, what, extra ->
            PlaybackLog.i("MediaPlayer onInfo what=$what extra=$extra")
            false
        }
    }

    private fun releaseBroken() {
        preparing = false
        try {
            release()
        } catch (error: Exception) {
            PlaybackLog.e("releaseBroken failed", error)
            player = null
        }
        released = false
    }

    private fun notifyError(error: PlaybackError) {
        postOnMain { listener?.onError(error) }
    }

    private fun postOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
