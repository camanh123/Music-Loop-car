package com.musicloop.car.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicloop.car.database.LibraryRepository
import com.musicloop.car.database.MediaItemEntity
import com.musicloop.car.library.MediaListRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Media3 playback facade. Plays directly from a resolved USB path. Never copies files.
 */
class Media3PlayerManager(
    context: Context,
    private val repository: LibraryRepository,
    resolver: MediaItemResolver,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    private val engine = object : PlaybackEngine {
        override fun prepareAndPlay(absolutePath: String) {
            val uri = Uri.fromFile(File(absolutePath))
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
            player.play()
        }

        override fun pause() {
            player.pause()
        }

        override fun play() {
            player.playWhenReady = true
            player.play()
        }

        override fun stop() {
            player.stop()
            player.clearMediaItems()
        }

        override fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
        }

        override fun position(): Long = player.currentPosition.coerceAtLeast(0L)

        override fun duration(): Long {
            val value = player.duration
            return if (value < 0L) 0L else value
        }

        override fun isPlaying(): Boolean = player.isPlaying

        override fun release() {
            player.release()
        }
    }

    val coordinator = PlaybackCoordinator(
        resolver = resolver,
        engine = engine,
        scope = scope
    )

    val state = coordinator.state

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                coordinator.publishPosition(engine.position(), engine.duration())
            } catch (_: Exception) {
                // Poll must never crash UI.
            }
            mainHandler.postDelayed(this, POSITION_POLL_MS)
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> coordinator.onEngineEnded()
                    Player.STATE_BUFFERING -> { /* coordinator already set BUFFERING */ }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: error.errorCodeName
                coordinator.onEngineError(message)
            }
        })
        mainHandler.post(pollRunnable)
    }

    fun playItem(row: MediaListRow) {
        scope.launch {
            val queue = try {
                withContext(Dispatchers.IO) {
                    repository.mediaForVolume(row.volumeId)
                        .filter { it.mediaType == row.mediaType }
                        .sortedBy { it.fileName.lowercase() }
                        .map { it.toPlayable() }
                }
            } catch (_: Exception) {
                listOf(row.toPlayable())
            }
            val index = queue.indexOfFirst { it.relativePath == row.relativePath && it.volumeId == row.volumeId }
                .takeIf { it >= 0 } ?: 0
            val items = if (queue.isEmpty()) listOf(row.toPlayable()) else queue
            coordinator.playQueue(items, index.coerceIn(items.indices))
        }
    }

    fun playPause() = coordinator.playPause()
    fun pause() = coordinator.pause()
    fun next() = coordinator.next()
    fun previous() = coordinator.previous()
    fun seekTo(positionMs: Long) = coordinator.seekTo(positionMs)
    fun stop() = coordinator.stop()

    fun onOnlineVolumesChanged(onlineVolumeIds: Set<String>) {
        coordinator.onOnlineVolumesChanged(onlineVolumeIds)
    }

    fun release() {
        mainHandler.removeCallbacks(pollRunnable)
        coordinator.release()
    }

    companion object {
        private const val POSITION_POLL_MS = 400L
    }
}

private fun MediaListRow.toPlayable(): PlayableRef {
    return PlayableRef(
        id = id,
        volumeId = volumeId,
        relativePath = relativePath,
        fileName = fileName,
        mediaType = mediaType,
        title = title,
        artist = artist
    )
}

private fun MediaItemEntity.toPlayable(): PlayableRef {
    return PlayableRef(
        id = id,
        volumeId = volumeId,
        relativePath = relativePath,
        fileName = fileName,
        mediaType = mediaType,
        title = title,
        artist = artist
    )
}
