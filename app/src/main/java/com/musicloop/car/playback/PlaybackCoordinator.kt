package com.musicloop.car.playback

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Queue + resolve + engine orchestration. No USB writes. No Room deletes.
 */
class PlaybackCoordinator(
    private val resolver: MediaItemResolver,
    private val engine: PlaybackEngine,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var queue: List<PlayableRef> = emptyList()
    private var index: Int = -1
    private var needsPrepare: Boolean = false
    /** Session-only Play Next / Add to Queue. Not persisted. */
    private val explicitItems = ArrayList<PlayableRef>()
    private val _explicitQueue = MutableStateFlow<List<PlayableRef>>(emptyList())
    val explicitQueue: StateFlow<List<PlayableRef>> = _explicitQueue.asStateFlow()
    private var playbackGeneration: Long = 0L
    private var autoAdvancedGeneration: Long = -1L

    fun markStarting(item: PlayableRef) {
        _state.update {
            it.copy(
                current = item,
                mode = if (item.mediaType == "VIDEO") PlayerMode.VIDEO else PlayerMode.AUDIO,
                status = PlayStatus.BUFFERING,
                errorMessage = null,
                positionMs = 0L,
                needsPrepare = false
            )
        }
        needsPrepare = false
    }

    fun playQueue(items: List<PlayableRef>, startIndex: Int) {
        if (items.isEmpty() || startIndex !in items.indices) {
            _state.update {
                it.copy(status = PlayStatus.ERROR, errorMessage = "Nothing to play")
            }
            return
        }
        queue = items
        index = startIndex
        playCurrent()
    }

    fun playPause() {
        val status = _state.value.status
        when (status) {
            PlayStatus.PLAYING, PlayStatus.BUFFERING -> pause()
            PlayStatus.PAUSED, PlayStatus.ENDED, PlayStatus.STOPPED -> resumeOrReplay()
            PlayStatus.IDLE, PlayStatus.ERROR -> {
                if (index in queue.indices) {
                    playCurrent()
                }
            }
        }
    }

    fun pause() {
        try {
            engine.pause()
        } catch (_: Exception) {
            // Keep UI in a safe paused/error state.
        }
        _state.update { it.copy(status = PlayStatus.PAUSED) }
    }

    fun resume() {
        resumeOrReplay()
    }

    fun stop() {
        stopInternal(status = PlayStatus.STOPPED, error = null)
    }

    fun next() {
        if (consumeExplicitQueueItem()) {
            return
        }
        if (queue.isEmpty()) {
            return
        }
        index = (index + 1).mod(queue.size)
        playCurrent()
    }

    fun previous() {
        if (queue.isEmpty()) {
            return
        }
        index = (index - 1).mod(queue.size)
        playCurrent()
    }

    fun hasNextItem(): Boolean {
        if (explicitItems.isNotEmpty()) {
            return true
        }
        return index >= 0 && index < queue.lastIndex
    }

    fun hasPreviousItem(): Boolean = index > 0

    fun skipToNext() {
        if (consumeExplicitQueueItem()) {
            return
        }
        if (!hasLibraryNext()) {
            return
        }
        index += 1
        playCurrent()
    }

    fun skipToPrevious() {
        if (!hasPreviousItem()) {
            return
        }
        index -= 1
        playCurrent()
    }

    fun playNext(item: PlayableRef) {
        explicitItems.add(0, item)
        publishExplicitQueue()
    }

    fun addToQueue(item: PlayableRef) {
        explicitItems.add(item)
        publishExplicitQueue()
    }

    fun removeQueued(index: Int) {
        if (index !in explicitItems.indices) {
            return
        }
        explicitItems.removeAt(index)
        publishExplicitQueue()
    }

    fun clearExplicitQueue() {
        if (explicitItems.isEmpty()) {
            return
        }
        explicitItems.clear()
        publishExplicitQueue()
    }

    fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        val clamped = positionMs.coerceAtLeast(0L).let { value ->
            if (duration > 0L) value.coerceAtMost(duration) else value
        }
        try {
            engine.seekTo(clamped)
        } catch (_: Exception) {
            _state.update { it.copy(status = PlayStatus.ERROR, errorMessage = "Seek failed") }
            return
        }
        _state.update { it.copy(positionMs = clamped) }
    }

    fun publishPosition(positionMs: Long, durationMs: Long) {
        _state.update {
            it.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L)
            )
        }
    }

    /**
     * Audio end-of-track uses [next] so Play Next / queue / library wrap stay
     * on one path. Video stays ENDED and does not auto-loop.
     *
     * STOPPED/ERROR/IDLE (USB drop, explicit stop, failure) ignore stale ENDED.
     * BUFFERING and a per-generation latch ignore duplicate ENDED for the same item.
     */
    fun onEngineEnded() {
        val state = _state.value
        when (state.status) {
            PlayStatus.STOPPED, PlayStatus.ERROR, PlayStatus.IDLE -> return
            PlayStatus.BUFFERING, PlayStatus.ENDED -> return
            PlayStatus.PLAYING, PlayStatus.PAUSED -> Unit
        }
        val current = state.current
        if (current == null || current.mediaType == "VIDEO") {
            _state.update { it.copy(status = PlayStatus.ENDED) }
            return
        }
        if (autoAdvancedGeneration == playbackGeneration) {
            return
        }
        autoAdvancedGeneration = playbackGeneration
        next()
    }

    /**
     * ExoPlayer paused itself (audio focus loss, becoming noisy, media pause).
     * Does not override USB disconnect / error.
     */
    fun onEnginePausedBySystem() {
        val status = _state.value.status
        if (status == PlayStatus.STOPPED || status == PlayStatus.ERROR || status == PlayStatus.IDLE) {
            return
        }
        _state.update { it.copy(status = PlayStatus.PAUSED) }
    }

    /**
     * ExoPlayer resumed itself (audio focus gain). Does not start a new item.
     */
    fun onEngineResumedBySystem() {
        val status = _state.value.status
        if (status == PlayStatus.STOPPED || status == PlayStatus.ERROR ||
            status == PlayStatus.IDLE || status == PlayStatus.ENDED
        ) {
            return
        }
        if (needsPrepare) {
            return
        }
        _state.update { it.copy(status = PlayStatus.PLAYING, errorMessage = null) }
    }

    fun onEngineError(message: String) {
        try {
            engine.stop()
        } catch (_: Exception) {
            // Ignore.
        }
        _state.update {
            it.copy(status = PlayStatus.ERROR, errorMessage = message)
        }
    }

    fun abandonUsbPlayback(reason: String) {
        stopInternal(status = PlayStatus.STOPPED, error = "USB disconnected")
        Log.i(USB_LOG, "player_abandon reason=$reason status=STOPPED")
    }

    fun onOnlineVolumesChanged(onlineVolumeIds: Set<String>) {
        val current = queue.getOrNull(index) ?: return
        if (current.volumeId !in onlineVolumeIds) {
            scope.launch(mainDispatcher) {
                abandonUsbPlayback("volume_offline volumeId=${current.volumeId}")
            }
            return
        }
        val state = _state.value
        if (state.status == PlayStatus.STOPPED && state.errorMessage == "USB disconnected") {
            // USB is back. Keep identity, do not auto-play.
            _state.update {
                it.copy(
                    status = PlayStatus.PAUSED,
                    errorMessage = null,
                    needsPrepare = true
                )
            }
        }
    }

    fun release() {
        try {
            engine.stop()
        } catch (_: Exception) {
            // Ignore.
        }
        try {
            engine.release()
        } catch (_: Exception) {
            // Ignore.
        }
        queue = emptyList()
        index = -1
        needsPrepare = false
        explicitItems.clear()
        publishExplicitQueue()
        playbackGeneration = 0L
        autoAdvancedGeneration = -1L
        _state.value = PlaybackUiState()
    }

    private fun hasLibraryNext(): Boolean = index >= 0 && index < queue.lastIndex

    private fun consumeExplicitQueueItem(): Boolean {
        if (explicitItems.isEmpty()) {
            return false
        }
        val item = explicitItems.removeAt(0)
        publishExplicitQueue()
        playItemInternal(item)
        return true
    }

    private fun publishExplicitQueue() {
        _explicitQueue.value = explicitItems.toList()
    }

    private fun resumeOrReplay() {
        val current = queue.getOrNull(index)
        if (current == null) {
            return
        }
        if (needsPrepare ||
            _state.value.status == PlayStatus.ENDED ||
            _state.value.status == PlayStatus.STOPPED
        ) {
            playCurrent()
            return
        }
        try {
            engine.play()
            _state.update { it.copy(status = PlayStatus.PLAYING, errorMessage = null) }
        } catch (_: Exception) {
            playCurrent()
        }
    }

    private fun playCurrent() {
        val item = queue.getOrNull(index) ?: return
        playItemInternal(item)
    }

    private fun playItemInternal(item: PlayableRef) {
        playbackGeneration += 1L
        _state.update {
            it.copy(
                current = item,
                mode = if (item.mediaType == "VIDEO") PlayerMode.VIDEO else PlayerMode.AUDIO,
                status = PlayStatus.BUFFERING,
                errorMessage = null,
                positionMs = 0L,
                needsPrepare = false
            )
        }
        needsPrepare = false
        scope.launch {
            val resolved = try {
                withContext(ioDispatcher) {
                    resolver.resolve(item.volumeId, item.relativePath)
                }
            } catch (_: Exception) {
                ResolveResult.Invalid("resolve failed")
            }
            when (resolved) {
                is ResolveResult.Ready -> {
                    try {
                        withContext(mainDispatcher) {
                            engine.prepareAndPlay(
                                absolutePath = resolved.absolutePath,
                                title = item.displayTitle,
                                artist = item.displayArtist,
                                mediaId = "${item.volumeId}:${item.relativePath}"
                            )
                        }
                        _state.update { it.copy(status = PlayStatus.PLAYING, errorMessage = null) }
                    } catch (_: Exception) {
                        _state.update {
                            it.copy(status = PlayStatus.ERROR, errorMessage = "Playback failed")
                        }
                    }
                }
                is ResolveResult.Offline -> _state.update {
                    it.copy(status = PlayStatus.ERROR, errorMessage = "USB offline")
                }
                is ResolveResult.Missing -> _state.update {
                    it.copy(status = PlayStatus.ERROR, errorMessage = "File missing")
                }
                is ResolveResult.Unsupported -> _state.update {
                    it.copy(status = PlayStatus.ERROR, errorMessage = "Unsupported media")
                }
                is ResolveResult.Invalid -> _state.update {
                    it.copy(status = PlayStatus.ERROR, errorMessage = resolved.reason)
                }
            }
        }
    }

    private fun stopInternal(status: PlayStatus, error: String?) {
        try {
            engine.stop()
        } catch (_: Exception) {
            // Ignore engine failures while stopping.
        }
        needsPrepare = true
        _state.update {
            it.copy(
                status = status,
                positionMs = 0L,
                errorMessage = error,
                needsPrepare = true
            )
        }
    }

    companion object {
        private const val USB_LOG = "MusicLoopUSB"
    }
}
