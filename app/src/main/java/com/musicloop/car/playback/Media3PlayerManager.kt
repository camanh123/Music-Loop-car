package com.musicloop.car.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
 *
 * ExoPlayer is process-owned and must be mutated on the main thread. USB disconnect
 * stops playback and clears the current MediaItem so FileDataSource can drop FDs.
 * The player instance is not destroyed on unplug (PlayerView may still be attached).
 */
class Media3PlayerManager(
    context: Context,
    private val repository: LibraryRepository,
    resolver: MediaItemResolver,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build().also { exo ->
        // Media3 standard noisy-audio handling (wired/BT disconnect). No custom receiver.
        exo.setHandleAudioBecomingNoisy(true)
        exo.setWakeMode(C.WAKE_MODE_LOCAL)
        // Media3 standard audio focus. handleAudioFocus=true — no custom focus listener.
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus= */ true
        )
    }

    @Volatile
    private var lastPreparedPath: String? = null

    @Volatile
    private var lastMime: String? = null

    @Volatile
    private var lastUri: Uri? = null

    private val engine = object : PlaybackEngine {
        override fun prepareAndPlay(absolutePath: String, title: String?, artist: String?, mediaId: String?) {
            runOnMainBlocking {
                val file = File(absolutePath)
                val mime = PlaybackMime.fromFileName(file.name)
                val uri = Uri.fromFile(file)
                lastPreparedPath = absolutePath
                lastMime = mime
                lastUri = uri
                if (PlaybackMime.isVideoFileName(file.name)) {
                    logVideoPlayback(absolutePath, mime, uri, playerError = "")
                }
                Log.i(
                    USB_LOG,
                    "player_prepare pathSet=${absolutePath.isNotBlank()} mime=${mime ?: "-"}"
                )
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(mediaId ?: file.name)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title?.takeIf { it.isNotBlank() } ?: file.name)
                            .setArtist(artist.orEmpty())
                            .setIsPlayable(true)
                            .build()
                    )
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
        }

        override fun pause() {
            runOnMainBlocking { player.pause() }
        }

        override fun play() {
            runOnMainBlocking {
                player.playWhenReady = true
                player.play()
            }
        }

        override fun stop() {
            runOnMainBlocking { stopAndDropUsbMedia("engine_stop") }
        }

        override fun seekTo(positionMs: Long) {
            runOnMainBlocking { player.seekTo(positionMs) }
        }

        override fun position(): Long = player.currentPosition.coerceAtLeast(0L)

        override fun duration(): Long {
            val value = player.duration
            return if (value < 0L) 0L else value
        }

        override fun isPlaying(): Boolean = player.isPlaying

        override fun release() {
            runOnMainBlocking {
                stopAndDropUsbMedia("engine_release")
                player.release()
                Log.i(USB_LOG, "player_release")
            }
        }
    }

    val coordinator = PlaybackCoordinator(
        resolver = resolver,
        engine = engine,
        scope = scope
    )

    val sessionPlayer: Player by lazy { CoordinatorForwardingPlayer(player, coordinator) }

    val state = coordinator.state

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                if (lastPreparedPath != null || player.isPlaying) {
                    coordinator.publishPosition(engine.position(), engine.duration())
                }
            } catch (_: Exception) {
                // Poll must never crash UI.
            }
            mainHandler.postDelayed(this, POSITION_POLL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> coordinator.onEngineEnded()
                Player.STATE_BUFFERING -> { /* coordinator already set BUFFERING */ }
                else -> Unit
            }
            syncSessionService()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Audio focus loss/gain and becoming-noisy pause/resume happen on the
            // ExoPlayer instance, not through CoordinatorForwardingPlayer.
            when (player.playbackState) {
                Player.STATE_IDLE, Player.STATE_ENDED -> Unit
                else -> {
                    if (playWhenReady) {
                        coordinator.onEngineResumedBySystem()
                    } else {
                        coordinator.onEnginePausedBySystem()
                    }
                }
            }
            syncSessionService()
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
            Log.i(USB_LOG, "playback_error $formatted")
            val message = error.message?.takeIf { it.isNotBlank() } ?: error.errorCodeName
            coordinator.onEngineError(message)
            runOnMain {
                stopAndDropUsbMedia("player_error")
                syncSessionService()
            }
        }
    }

    init {
        player.addListener(playerListener)
        mainHandler.post(pollRunnable)
    }

    fun playItem(row: MediaListRow) {
        if (row.mediaType == "AUDIO") {
            MusicLoopPlaybackService.ensureStarted(appContext)
        } else {
            MusicLoopPlaybackService.stop(appContext)
        }
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

    fun playPause() {
        coordinator.playPause()
        syncSessionService()
    }
    fun pause() {
        coordinator.pause()
        syncSessionService()
    }
    fun next() {
        coordinator.next()
        syncSessionService()
    }
    fun previous() {
        coordinator.previous()
        syncSessionService()
    }
    fun seekTo(positionMs: Long) = coordinator.seekTo(positionMs)
    fun stop() {
        coordinator.stop()
        syncSessionService()
    }

    fun playNext(row: MediaListRow) {
        coordinator.playNext(row.toPlayable())
        syncSessionService()
    }

    fun enqueue(row: MediaListRow) {
        coordinator.addToQueue(row.toPlayable())
        syncSessionService()
    }

    fun removeQueued(index: Int) {
        coordinator.removeQueued(index)
        syncSessionService()
    }

    fun clearExplicitQueue() {
        coordinator.clearExplicitQueue()
        syncSessionService()
    }

    fun playItems(items: List<MediaListRow>, startIndex: Int) {
        val audio = items.getOrNull(startIndex)?.mediaType != "VIDEO"
        if (audio) {
            MusicLoopPlaybackService.ensureStarted(appContext)
        } else {
            MusicLoopPlaybackService.stop(appContext)
        }
        val playable = items.map { it.toPlayable() }
        if (playable.isEmpty() || startIndex !in playable.indices) {
            return
        }
        coordinator.markStarting(playable[startIndex])
        coordinator.playQueue(playable, startIndex)
    }

    fun onOnlineVolumesChanged(onlineVolumeIds: Set<String>) {
        coordinator.onOnlineVolumesChanged(onlineVolumeIds)
        syncSessionService()
    }

    /**
     * Push ExoPlayer play/pause into coordinator UI after returning to MainActivity.
     * Does not start a new item or create another player.
     */
    fun reconcileUiFromPlayer() {
        runOnMain {
            when (player.playbackState) {
                Player.STATE_IDLE, Player.STATE_ENDED -> Unit
                else -> {
                    if (player.playWhenReady && player.isPlaying) {
                        coordinator.onEngineResumedBySystem()
                    } else if (!player.playWhenReady) {
                        coordinator.onEnginePausedBySystem()
                    }
                }
            }
            syncSessionService()
        }
    }

    /**
     * Drop USB media resources immediately. Must be safe to call from any thread.
     * Does not destroy the ExoPlayer instance (VideoActivity may still be attached).
     */
    fun releaseUsbMedia(reason: String) {
        runOnMain {
            coordinator.abandonUsbPlayback(reason)
            stopAndDropUsbMedia(reason)
            syncSessionService()
        }
    }

    fun release() {
        mainHandler.removeCallbacks(pollRunnable)
        coordinator.release()
    }

    private fun syncSessionService() {
        if (BackgroundPlaybackPolicy.shouldHoldService(coordinator.state.value)) {
            MusicLoopPlaybackService.ensureStarted(appContext)
        } else {
            MusicLoopPlaybackService.stop(appContext)
        }
    }

    private fun stopAndDropUsbMedia(reason: String) {
        try {
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) {
            // Disconnect cleanup must not crash.
        }
        lastPreparedPath = null
        lastMime = null
        lastUri = null
        Log.i(USB_LOG, "player_usb_drop reason=$reason mediaCleared=true")
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun runOnMainBlocking(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Playback calls must not hang the USB lifecycle forever.
        }
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
        private const val USB_LOG = "MusicLoopUSB"
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
