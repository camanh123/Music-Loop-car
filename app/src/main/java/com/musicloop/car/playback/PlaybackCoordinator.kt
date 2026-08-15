package com.musicloop.car.playback

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

    fun onEngineEnded() {
        _state.update { it.copy(status = PlayStatus.ENDED) }
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

    fun onOnlineVolumesChanged(onlineVolumeIds: Set<String>) {
        val current = queue.getOrNull(index) ?: return
        if (current.volumeId !in onlineVolumeIds) {
            stopInternal(status = PlayStatus.STOPPED, error = "USB disconnected")
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
        _state.value = PlaybackUiState()
    }

    private fun resumeOrReplay() {
        val current = queue.getOrNull(index)
        if (current == null) {
            return
        }
        if (_state.value.status == PlayStatus.ENDED || _state.value.status == PlayStatus.STOPPED) {
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
        _state.update {
            it.copy(
                current = item,
                mode = if (item.mediaType == "VIDEO") PlayerMode.VIDEO else PlayerMode.AUDIO,
                status = PlayStatus.BUFFERING,
                errorMessage = null,
                positionMs = 0L
            )
        }
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
                            engine.prepareAndPlay(resolved.absolutePath)
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
        _state.update {
            it.copy(
                status = status,
                positionMs = 0L,
                errorMessage = error
            )
        }
    }
}
