package com.musicloop.car.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    @Volatile
    private var lastPreparedPath: String? = null

    @Volatile
    private var lastMime: String? = null

    @Volatile
    private var lastUri: Uri? = null

    private val engine = object : PlaybackEngine {
        override fun prepareAndPlay(absolutePath: String) {
            val file = File(absolutePath)
            val mime = PlaybackMime.fromFileName(file.name)
            val uri = Uri.fromFile(file)
            lastPreparedPath = absolutePath
            lastMime = mime
            lastUri = uri
            if (PlaybackMime.isVideoFileName(file.name)) {
                logVideoPlayback(absolutePath, mime, uri, playerError = "")
            }
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .apply {
                    if (mime != null) {
                        setMimeType(mime)
                    }
                }
                .build()
            player.setMediaItem(mediaItem)
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
                val path = lastPreparedPath.orEmpty()
                val mime = lastMime
                val uri = lastUri ?: Uri.EMPTY
                val formatted = PlaybackErrorClassifier.format(
                    errorCodeName = error.errorCodeName,
                    message = error.message,
                    causeName = error.cause?.javaClass?.name,
                    causeMessage = error.cause?.message
                )
                logVideoPlayback(path, mime, uri, playerError = formatted)
                val message = error.message?.takeIf { it.isNotBlank() } ?: error.errorCodeName
                coordinator.onEngineError(message)
            }
        })
        mainHandler.post(pollRunnable)
    }

    fun playItem(row: MediaListRow) {
        coordinator.markStarting(row.toPlayable())
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

    private fun logVideoPlayback(path: String, mime: String?, uri: Uri, playerError: String) {
        val file = File(path)
        val exists = try {
            file.exists()
        } catch (_: Exception) {
            false
        }
        val canRead = try {
            file.canRead()
        } catch (_: Exception) {
            false
        }
        Log.i(
            VIDEO_LOG_TAG,
            "path=$path exists=$exists canRead=$canRead mimeType=${mime ?: "-"} uri=$uri playerError=${playerError.ifBlank { "-" }}"
        )
    }

    companion object {
        private const val POSITION_POLL_MS = 400L
        private const val VIDEO_LOG_TAG = "VIDEO_PLAYBACK"
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
