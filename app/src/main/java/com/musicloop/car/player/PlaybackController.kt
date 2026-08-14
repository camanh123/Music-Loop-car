package com.musicloop.car.player

/**
 * UI → PlaybackController → PlayerEngine → MediaPlayerEngine → USB file (read-only).
 *
 * Owns player state, library-queue navigation, resume clamping, and USB-disconnect
 * stop. Does not auto-play after USB reconnect or app restart.
 */
class PlaybackController(
    private val engine: PlayerEngine,
    private val store: PlaybackStateRepository,
    private val audioFocus: AudioFocusGate,
    private val fileAccess: TrackFileAccess,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val listener: (PlaybackSnapshot) -> Unit
) {
    private var queue: List<QueueItem> = emptyList()
    private var currentIndex: Int = -1
    private var current: QueueItem? = null
    private var state: PlayerState = PlayerState.IDLE
    private var positionMs: Int = 0
    private var durationMs: Int = 0
    private var pendingSeekMs: Int = 0
    private var repeatMode: RepeatMode = store.loadRepeatMode()
    private var message: PlaybackMessage = PlaybackMessage.NONE
    private var error: PlaybackError? = null
    private var volumeIdentity: String? = null
    private var volumeRoot: String? = null
    private var playGeneration: Int = 0
    private var prepareGeneration: Int = 0
    private var lastPersistAt: Long = 0L
    private var released: Boolean = false

    init {
        engine.setListener(object : PlayerEngineListener {
            override fun onPrepared(durationMs: Int) {
                handlePrepared(durationMs)
            }

            override fun onCompletion() {
                handleCompletion()
            }

            override fun onError(error: PlaybackError) {
                handleError(error)
            }
        })
    }

    fun snapshot(): PlaybackSnapshot {
        return PlaybackSnapshot(
            state = state,
            track = current,
            positionMs = positionMs,
            durationMs = durationMs,
            repeatMode = repeatMode,
            message = message,
            error = error
        )
    }

    fun setVolumeContext(volumeIdentity: String?, volumeRoot: String?, usbConnected: Boolean) {
        this.volumeIdentity = volumeIdentity
        if (!usbConnected) {
            onUsbDisconnected()
            return
        }
        this.volumeRoot = volumeRoot
        val playing = current
        if (playing != null &&
            volumeIdentity != null &&
            playing.volumeIdentity != volumeIdentity
        ) {
            current = null
            currentIndex = -1
            positionMs = 0
            durationMs = 0
            pendingSeekMs = 0
        }
        if (state == PlayerState.USB_DISCONNECTED) {
            state = PlayerState.IDLE
            message = PlaybackMessage.NONE
        }
        if (current == null) {
            tryRestoreFromStore()
        } else {
            emit()
        }
    }

    fun setQueue(items: List<QueueItem>) {
        queue = items
        val playing = current
        if (playing != null) {
            val index = items.indexOfFirst { it.sameTrack(playing) }
            currentIndex = index
            if (index >= 0) {
                current = items[index]
            }
        } else {
            tryRestoreFromStore()
        }
    }

    fun playUserSelected(item: QueueItem) {
        if (state == PlayerState.USB_DISCONNECTED || volumeRoot == null) {
            state = PlayerState.USB_DISCONNECTED
            message = PlaybackMessage.USB_DISCONNECTED
            emit()
            return
        }
        val index = indexOf(item)
        startTrack(item, index, resumeFromMs = 0, userInitiated = true)
    }

    fun playPause() {
        when (state) {
            PlayerState.PLAYING -> pause()
            PlayerState.PREPARING -> Unit
            PlayerState.USB_DISCONNECTED -> {
                message = PlaybackMessage.USB_DISCONNECTED
                emit()
            }
            PlayerState.PAUSED -> resumePaused()
            PlayerState.IDLE, PlayerState.COMPLETED, PlayerState.ERROR -> {
                val item = current ?: return
                startTrack(item, currentIndex, pendingSeekOrSaved(), userInitiated = true)
            }
        }
    }

    fun pause() {
        if (state != PlayerState.PLAYING) {
            return
        }
        try {
            positionMs = engine.currentPosition()
            engine.pause()
        } catch (_: Exception) {
        }
        state = PlayerState.PAUSED
        persist(force = true)
        emit()
    }

    fun seekTo(positionMs: Int) {
        if (current == null || state == PlayerState.USB_DISCONNECTED || state == PlayerState.PREPARING) {
            return
        }
        val clamped = ResumeValidator.clampPosition(positionMs, durationMs.takeIf { it > 0 })
        this.positionMs = clamped
        pendingSeekMs = clamped
        if (state == PlayerState.PLAYING || state == PlayerState.PAUSED) {
            try {
                engine.seekTo(clamped)
            } catch (_: Exception) {
            }
        }
        persist(force = true)
        emit()
    }

    fun next() {
        advance(direction = 1, wrap = true)
    }

    fun previous() {
        if (current == null) {
            return
        }
        val position = livePosition()
        if (QueueNavigator.previousAction(position) == PreviousAction.RESTART) {
            seekTo(0)
            if (state == PlayerState.COMPLETED || state == PlayerState.IDLE || state == PlayerState.ERROR) {
                playPause()
            }
            return
        }
        advance(direction = -1, wrap = true)
    }

    fun cycleRepeatMode() {
        repeatMode = if (repeatMode == RepeatMode.OFF) RepeatMode.ONE else RepeatMode.OFF
        store.saveRepeatMode(repeatMode)
        emit()
    }

    fun onProgressTick(): PlaybackSnapshot {
        if (state == PlayerState.PLAYING) {
            positionMs = livePosition()
            persist(force = false)
        }
        return snapshot()
    }

    fun onUsbDisconnected() {
        if (state == PlayerState.USB_DISCONNECTED && volumeRoot == null) {
            return
        }
        positionMs = livePosition()
        persist(force = true)
        PlaybackLog.usbDisconnected(current?.filename)
        stopEngine(abandonFocus = true)
        volumeRoot = null
        state = PlayerState.USB_DISCONNECTED
        message = PlaybackMessage.USB_DISCONNECTED
        emit()
    }

    fun restoreDisplayOnly() {
        tryRestoreFromStore()
    }

    fun release() {
        released = true
        positionMs = livePosition()
        persist(force = true)
        stopEngine(abandonFocus = true)
        engine.setListener(null)
        engine.release()
    }

    private fun resumePaused() {
        audioFocus.requestFocus()
        try {
            engine.start()
            state = PlayerState.PLAYING
            message = PlaybackMessage.NONE
            emit()
        } catch (error: Exception) {
            handleError(PlaybackErrors.prepareFailed(error.message))
        }
    }

    private fun startTrack(
        item: QueueItem,
        index: Int,
        resumeFromMs: Int,
        userInitiated: Boolean
    ) {
        if (released) {
            return
        }
        val path = fileAccess.resolveReadable(volumeRoot, item.relativePath)
        if (path == null) {
            if (userInitiated) {
                current = item
                currentIndex = index
                state = PlayerState.ERROR
                error = PlaybackErrors.missingFile()
                message = PlaybackMessage.FILE_MISSING
                persist(force = true)
                emit()
            }
            return
        }
        playGeneration++
        prepareGeneration = playGeneration
        current = item
        currentIndex = index
        durationMs = item.durationMs?.toInt()?.coerceAtLeast(0) ?: 0
        pendingSeekMs = ResumeValidator.clampPosition(resumeFromMs, durationMs.takeIf { it > 0 })
        positionMs = pendingSeekMs
        error = null
        message = PlaybackMessage.PREPARING
        state = PlayerState.PREPARING
        persist(force = true)
        emit()
        PlaybackLog.prepareAttempt(item.filename, item.extension, path)
        try {
            engine.prepare(path)
        } catch (prepareError: Exception) {
            handleError(PlaybackErrors.prepareFailed(prepareError.message))
        }
    }

    private fun handlePrepared(preparedDurationMs: Int) {
        if (released ||
            playGeneration != prepareGeneration ||
            state == PlayerState.USB_DISCONNECTED ||
            state != PlayerState.PREPARING
        ) {
            return
        }
        val item = current ?: return
        durationMs = preparedDurationMs.coerceAtLeast(0)
        val seekTo = ResumeValidator.clampPosition(pendingSeekMs, durationMs)
        pendingSeekMs = 0
        positionMs = seekTo
        PlaybackLog.prepared(item.filename, item.extension, durationMs)
        if (seekTo > 0) {
            engine.seekTo(seekTo)
        }
        audioFocus.requestFocus()
        try {
            engine.start()
            state = PlayerState.PLAYING
            message = PlaybackMessage.NONE
            PlaybackLog.playbackStarted(item.filename, item.extension)
            persist(force = true)
            emit()
        } catch (startError: Exception) {
            handleError(PlaybackErrors.prepareFailed(startError.message))
        }
    }

    private fun handleCompletion() {
        if (released ||
            playGeneration != prepareGeneration ||
            state == PlayerState.USB_DISCONNECTED
        ) {
            return
        }
        positionMs = durationMs
        state = PlayerState.COMPLETED
        persist(force = true)
        if (repeatMode == RepeatMode.ONE) {
            val item = current ?: return
            startTrack(item, currentIndex, resumeFromMs = 0, userInitiated = true)
            return
        }
        emit()
        advance(direction = 1, wrap = false)
    }

    private fun handleError(playbackError: PlaybackError) {
        if (released || state == PlayerState.USB_DISCONNECTED) {
            return
        }
        val item = current
        if (item != null) {
            PlaybackLog.playbackError(item.filename, item.extension, playbackError)
        }
        stopEngine(abandonFocus = true)
        error = playbackError
        state = PlayerState.ERROR
        message = when (playbackError.kind) {
            PlaybackErrorKind.MISSING_FILE -> PlaybackMessage.FILE_MISSING
            else -> PlaybackMessage.CANNOT_PLAY_FILE
        }
        persist(force = true)
        emit()
    }

    private fun advance(direction: Int, wrap: Boolean) {
        if (state == PlayerState.USB_DISCONNECTED) {
            message = PlaybackMessage.USB_DISCONNECTED
            emit()
            return
        }
        if (queue.isEmpty()) {
            stopAfterQueueExhausted()
            return
        }
        val result = QueueNavigator.findPlayable(
            size = queue.size,
            fromIndex = currentIndex.coerceAtLeast(0),
            direction = direction,
            wrap = wrap
        ) { index -> canAutoPlay(queue[index]) }

        when {
            result.exhausted -> stopAfterQueueExhausted()
            result.ended -> {
                state = PlayerState.COMPLETED
                audioFocus.abandonFocus()
                try {
                    engine.stop()
                } catch (_: Exception) {
                }
                message = PlaybackMessage.NONE
                persist(force = true)
                emit()
            }
            result.index != null -> {
                val item = queue[result.index]
                startTrack(item, result.index, resumeFromMs = 0, userInitiated = true)
            }
        }
    }

    private fun canAutoPlay(item: QueueItem): Boolean {
        if (!item.playable) {
            return false
        }
        return fileAccess.resolveReadable(volumeRoot, item.relativePath) != null
    }

    private fun stopAfterQueueExhausted() {
        stopEngine(abandonFocus = true)
        state = if (current == null) PlayerState.IDLE else PlayerState.COMPLETED
        message = PlaybackMessage.NO_PLAYABLE_TRACK
        persist(force = true)
        emit()
    }

    private fun stopEngine(abandonFocus: Boolean) {
        playGeneration++
        if (abandonFocus) {
            audioFocus.abandonFocus()
        }
        try {
            engine.stop()
        } catch (_: Exception) {
        }
        try {
            engine.release()
        } catch (_: Exception) {
        }
    }

    private fun tryRestoreFromStore() {
        val saved = store.load() ?: return
        if (volumeIdentity != null && saved.volumeIdentity != volumeIdentity) {
            return
        }
        val fromQueue = queue.firstOrNull {
            ResumeValidator.sameTrack(
                saved.volumeIdentity,
                saved.relativePath,
                it.volumeIdentity,
                it.relativePath
            )
        }
        if (fromQueue == null && queue.isNotEmpty()) {
            return
        }
        val item = fromQueue ?: QueueItem(
            id = -1L,
            volumeIdentity = saved.volumeIdentity,
            relativePath = saved.relativePath,
            title = saved.title.ifBlank { saved.relativePath.substringAfterLast('/') },
            artist = saved.artist,
            album = saved.album,
            durationMs = saved.durationMs.takeIf { it > 0 }?.toLong(),
            filename = saved.relativePath.substringAfterLast('/'),
            extension = saved.relativePath.substringAfterLast('.', ""),
            playable = true
        )
        current = item
        currentIndex = if (fromQueue == null) -1 else queue.indexOf(fromQueue)
        durationMs = saved.durationMs
        pendingSeekMs = ResumeValidator.clampPosition(
            saved.positionMs,
            durationMs.takeIf { it > 0 }
        )
        positionMs = pendingSeekMs
        if (state != PlayerState.USB_DISCONNECTED) {
            state = PlayerState.IDLE
            message = PlaybackMessage.NONE
        }
        emit()
    }

    private fun pendingSeekOrSaved(): Int {
        if (pendingSeekMs > 0) {
            return pendingSeekMs
        }
        return positionMs
    }

    private fun livePosition(): Int {
        return try {
            val live = engine.currentPosition()
            if (live > 0) live else positionMs
        } catch (_: Exception) {
            positionMs
        }
    }

    private fun persist(force: Boolean) {
        val item = current ?: return
        val now = clock()
        if (!force && now - lastPersistAt < PERSIST_INTERVAL_MS) {
            return
        }
        lastPersistAt = now
        store.save(
            SavedPlaybackState(
                volumeIdentity = item.volumeIdentity,
                relativePath = item.relativePath,
                positionMs = positionMs,
                updatedAt = now,
                title = item.title,
                artist = item.artist,
                album = item.album,
                durationMs = durationMs
            )
        )
    }

    private fun indexOf(item: QueueItem): Int {
        val byIdentity = queue.indexOfFirst { it.sameTrack(item) }
        if (byIdentity >= 0) {
            return byIdentity
        }
        return queue.indexOfFirst { it.id == item.id && item.id > 0 }
    }

    private fun emit() {
        listener(snapshot())
    }

    companion object {
        const val PERSIST_INTERVAL_MS = 5_000L
        const val PROGRESS_INTERVAL_MS = 750L
    }
}
