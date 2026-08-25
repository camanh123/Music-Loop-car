package com.musicloop.car

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.musicloop.car.databinding.ActivityVideoBinding
import com.musicloop.car.library.MediaListRow
import com.musicloop.car.playback.PlayStatus
import com.musicloop.car.playback.PlaybackUiState
import com.musicloop.car.playback.PositionSaveGate
import com.musicloop.car.playback.VideoPlaybackGuard
import com.musicloop.car.playback.VideoPlaybackStore
import com.musicloop.car.playback.VideoRestore
import kotlinx.coroutines.launch
import java.util.Locale

class VideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoBinding
    private lateinit var store: VideoPlaybackStore
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }
    private val positionGate = PositionSaveGate()

    private var userSeeking = false
    private var overlayVisible = true
    private var waitingForUsb = false
    private var pendingSeekMs = 0L
    private var seekApplied = false
    private var pauseAfterSeek = false
    private var lastGoodPositionMs = 0L
    private var missingFallbackUsed = false
    private var lastPlayStatus: PlayStatus? = null
    private var currentRow: MediaListRow? = null
    private var videoRows: List<MediaListRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = VideoPlaybackStore(applicationContext)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindControls()
        val row = intent.toMediaRow()
        if (row == null) {
            finish()
            return
        }
        currentRow = row
        binding.playerView.player = musicLoopApp().playerManager.player
        val saved = store.load()
        val sameVideo = VideoRestore.matches(row, saved)
        val usbOnline = musicLoopApp().lifecycleController.uiState.value.usbOnline
        if (usbOnline) {
            val seek = if (sameVideo) saved.positionMs else 0L
            pauseAfterSeek = sameVideo && !saved.playWhenReady
            startVideo(row, seek)
        } else {
            persistIdentity(row, resetPosition = false)
            waitingForUsb = true
            showWaitingMessage(getString(R.string.video_usb_waiting))
            showOverlay(autoHide = false)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().playerManager.state.collect { render(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().lifecycleController.uiState.collect { state ->
                    videoRows = state.media.filter { it.mediaType == "VIDEO" }
                    updateTransportEnabled()
                    onUsbLibrary(state.usbOnline)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.playerView.player = musicLoopApp().playerManager.player
    }

    override fun onPause() {
        persistPosition(force = true)
        super.onPause()
    }

    override fun onStop() {
        hideHandler.removeCallbacks(hideRunnable)
        persistPosition(force = true)
        binding.playerView.player = null
        if (isFinishing) {
            musicLoopApp().playerManager.pause()
        }
        super.onStop()
    }

    private fun bindControls() {
        binding.buttonPlayPause.setOnClickListener {
            musicLoopApp().playerManager.playPause()
            showOverlay(autoHide = true)
        }
        binding.buttonClose.setOnClickListener {
            persistPosition(force = true)
            finish()
        }
        binding.buttonPrevious.setOnClickListener { playAdjacent(-1) }
        binding.buttonNext.setOnClickListener { playAdjacent(1) }
        binding.buttonRewind.setOnClickListener { seekBy(-SEEK_STEP_MS) }
        binding.buttonForward.setOnClickListener { seekBy(SEEK_STEP_MS) }
        binding.buttonFullscreen.setOnClickListener { hideOverlay() }
        binding.clickCatcher.setOnClickListener { showOverlay(autoHide = true) }
        binding.overlayScrim.setOnClickListener { hideOverlay() }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                val duration = musicLoopApp().playerManager.state.value.durationMs
                val position = if (duration <= 0L) 0L else (duration * progress) / SEEK_MAX
                binding.positionText.text = formatMs(position)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
                hideHandler.removeCallbacks(hideRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                val duration = musicLoopApp().playerManager.state.value.durationMs
                val position = if (duration <= 0L) 0L else (duration * (seekBar?.progress ?: 0)) / SEEK_MAX
                musicLoopApp().playerManager.seekTo(position)
                lastGoodPositionMs = position
                persistPosition(force = true)
                showOverlay(autoHide = true)
            }
        })
    }

    private fun onUsbLibrary(usbOnline: Boolean) {
        if (!usbOnline) {
            if (!waitingForUsb) {
                persistPosition(force = true)
            }
            waitingForUsb = true
            showWaitingMessage(getString(R.string.video_usb_waiting))
            showOverlay(autoHide = false)
            return
        }
        if (!waitingForUsb || videoRows.isEmpty()) {
            return
        }
        waitingForUsb = false
        missingFallbackUsed = false
        val saved = store.load()
        val target = VideoRestore.resolveCurrent(videoRows, saved) ?: return
        currentRow = target.item
        pauseAfterSeek = target.matchedSaved && !saved.playWhenReady
        val seek = if (target.matchedSaved) saved.positionMs else 0L
        if (!target.matchedSaved) {
            showWaitingMessage(getString(R.string.video_missing_fallback))
        } else {
            clearWaitingMessage()
        }
        startVideo(target.item, seek)
    }

    private fun startVideo(row: MediaListRow, restorePositionMs: Long) {
        currentRow = row
        pendingSeekMs = restorePositionMs.coerceAtLeast(0L)
        seekApplied = pendingSeekMs <= 0L
        lastGoodPositionMs = pendingSeekMs
        persistIdentity(row, resetPosition = false)
        store.savePosition(pendingSeekMs, !pauseAfterSeek, System.currentTimeMillis())
        musicLoopApp().playerManager.playItem(row)
        updateTransportEnabled()
        showOverlay(autoHide = true)
    }

    private fun playAdjacent(delta: Int) {
        val next = VideoRestore.adjacent(videoRows, currentRow, delta) ?: return
        persistPosition(force = true)
        pauseAfterSeek = false
        missingFallbackUsed = false
        startVideo(next, 0L)
        store.savePosition(0L, true, System.currentTimeMillis())
    }

    private fun seekBy(deltaMs: Long) {
        val state = musicLoopApp().playerManager.state.value
        val target = (state.positionMs + deltaMs).coerceAtLeast(0L)
        musicLoopApp().playerManager.seekTo(target)
        lastGoodPositionMs = target
        persistPosition(force = true)
        showOverlay(autoHide = true)
    }

    private fun render(state: PlaybackUiState) {
        val title = state.current?.displayTitle ?: currentRow?.let { it.title?.takeIf { value -> value.isNotBlank() } ?: it.fileName }
        binding.titleText.text = title ?: getString(R.string.player_idle)
        if (!userSeeking) {
            binding.positionText.text = formatMs(state.positionMs)
            binding.durationText.text = formatMs(state.durationMs)
        }
        binding.buttonPlayPause.text = if (state.status == PlayStatus.PLAYING) {
            getString(R.string.pause)
        } else {
            getString(R.string.play)
        }
        if (!waitingForUsb) {
            val error = state.errorMessage.orEmpty()
            if (error.isBlank()) {
                clearWaitingMessage()
            } else {
                showWaitingMessage(getString(R.string.playback_error, error))
            }
        }
        if (!userSeeking && state.durationMs > 0L) {
            binding.seekBar.progress = ((state.positionMs * SEEK_MAX) / state.durationMs).toInt().coerceIn(0, SEEK_MAX)
        }
        if (state.positionMs > 0L && !VideoPlaybackGuard.isUsbLossForCurrentVideo(currentRow?.relativePath, state)) {
            lastGoodPositionMs = state.positionMs
        }
        maybeFallbackMissing(state)
        maybeRestoreSeek(state)
        maybePersistPlaying(state)
        if (state.status != lastPlayStatus) {
            lastPlayStatus = state.status
            if (state.status == PlayStatus.PLAYING && overlayVisible && !waitingForUsb) {
                scheduleHide()
            } else if (state.status != PlayStatus.PLAYING) {
                showOverlay(autoHide = false)
            }
        }
        updateTransportEnabled()
    }

    private fun maybeFallbackMissing(state: PlaybackUiState) {
        if (waitingForUsb || missingFallbackUsed) {
            return
        }
        if (state.errorMessage != "File missing") {
            return
        }
        missingFallbackUsed = true
        val current = currentRow
        val others = videoRows.filterNot { row ->
            current != null && row.volumeId == current.volumeId && row.relativePath == current.relativePath
        }
        val fallback = others.firstOrNull() ?: return
        showWaitingMessage(getString(R.string.video_missing_fallback))
        pauseAfterSeek = false
        startVideo(fallback, 0L)
    }

    private fun maybeRestoreSeek(state: PlaybackUiState) {
        if (seekApplied || pendingSeekMs <= 0L) {
            return
        }
        if (state.status != PlayStatus.PLAYING && state.status != PlayStatus.PAUSED) {
            return
        }
        if (state.durationMs <= 0L) {
            return
        }
        val clamped = pendingSeekMs.coerceAtMost((state.durationMs - 500L).coerceAtLeast(0L))
        seekApplied = true
        pendingSeekMs = 0L
        if (clamped > 0L) {
            musicLoopApp().playerManager.seekTo(clamped)
            lastGoodPositionMs = clamped
        }
        if (pauseAfterSeek) {
            pauseAfterSeek = false
            musicLoopApp().playerManager.pause()
        }
    }

    private fun maybePersistPlaying(state: PlaybackUiState) {
        if (waitingForUsb || VideoPlaybackGuard.isUsbLossForCurrentVideo(currentRow?.relativePath, state)) {
            return
        }
        val playing = state.status == PlayStatus.PLAYING
        val paused = state.status == PlayStatus.PAUSED
        if (!playing && !paused) {
            return
        }
        val force = paused
        if (positionGate.shouldSave(System.currentTimeMillis(), state.positionMs, force)) {
            store.savePosition(state.positionMs, playing, System.currentTimeMillis())
        }
    }

    private fun persistIdentity(row: MediaListRow, resetPosition: Boolean) {
        store.saveIdentity(
            volumeId = row.volumeId,
            relativePath = row.relativePath,
            fileName = row.fileName,
            title = row.title,
            nowMs = System.currentTimeMillis(),
            resetPosition = resetPosition
        )
    }

    private fun persistPosition(force: Boolean) {
        val state = musicLoopApp().playerManager.state.value
        val position = when {
            waitingForUsb -> lastGoodPositionMs
            state.positionMs > 0L -> state.positionMs
            else -> lastGoodPositionMs
        }
        val playing = !waitingForUsb && state.status == PlayStatus.PLAYING
        if (force || positionGate.shouldSave(System.currentTimeMillis(), position, force = true)) {
            store.savePosition(position, playing, System.currentTimeMillis())
        }
    }

    private fun updateTransportEnabled() {
        val previous = VideoRestore.adjacent(videoRows, currentRow, -1)
        val next = VideoRestore.adjacent(videoRows, currentRow, 1)
        binding.buttonPrevious.isEnabled = previous != null
        binding.buttonNext.isEnabled = next != null
    }

    private fun showOverlay(autoHide: Boolean) {
        overlayVisible = true
        binding.overlay.visibility = View.VISIBLE
        binding.clickCatcher.visibility = View.GONE
        hideHandler.removeCallbacks(hideRunnable)
        if (autoHide && musicLoopApp().playerManager.state.value.status == PlayStatus.PLAYING && !waitingForUsb) {
            scheduleHide()
        }
    }

    private fun hideOverlay() {
        if (waitingForUsb) {
            return
        }
        overlayVisible = false
        binding.overlay.visibility = View.GONE
        binding.clickCatcher.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun showWaitingMessage(message: String) {
        binding.errorText.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    private fun clearWaitingMessage() {
        binding.errorText.visibility = View.GONE
        binding.errorText.text = ""
    }

    private fun musicLoopApp(): MusicLoopApp = application as MusicLoopApp

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%d:%02d", total / 60L, total % 60L)
    }

    companion object {
        private const val SEEK_MAX = 1000
        private const val SEEK_STEP_MS = 10_000L
        private const val HIDE_DELAY_MS = 4_000L
        private const val EXTRA_ID = "id"
        private const val EXTRA_VOLUME_ID = "volumeId"
        private const val EXTRA_RELATIVE_PATH = "relativePath"
        private const val EXTRA_FILE_NAME = "fileName"
        private const val EXTRA_EXTENSION = "extension"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"

        fun intent(context: Context, row: MediaListRow): Intent {
            return Intent(context, VideoActivity::class.java)
                .putExtra(EXTRA_ID, row.id)
                .putExtra(EXTRA_VOLUME_ID, row.volumeId)
                .putExtra(EXTRA_RELATIVE_PATH, row.relativePath)
                .putExtra(EXTRA_FILE_NAME, row.fileName)
                .putExtra(EXTRA_EXTENSION, row.extension)
                .putExtra(EXTRA_TITLE, row.title)
                .putExtra(EXTRA_ARTIST, row.artist)
        }

        private fun Intent.toMediaRow(): MediaListRow? {
            val volumeId = getStringExtra(EXTRA_VOLUME_ID) ?: return null
            val relativePath = getStringExtra(EXTRA_RELATIVE_PATH) ?: return null
            return MediaListRow(
                id = getLongExtra(EXTRA_ID, 0L),
                volumeId = volumeId,
                relativePath = relativePath,
                fileName = getStringExtra(EXTRA_FILE_NAME) ?: relativePath.substringAfterLast('/'),
                extension = getStringExtra(EXTRA_EXTENSION).orEmpty(),
                mediaType = "VIDEO",
                sizeBytes = 0L,
                durationMs = null,
                title = getStringExtra(EXTRA_TITLE),
                artist = getStringExtra(EXTRA_ARTIST),
                album = null,
                scanStatus = ""
            )
        }
    }
}
