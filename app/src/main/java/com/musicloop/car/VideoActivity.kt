package com.musicloop.car

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.musicloop.car.databinding.ActivityVideoBinding
import com.musicloop.car.library.MediaListRow
import com.musicloop.car.playback.PlayStatus
import com.musicloop.car.playback.PlaybackUiState
import kotlinx.coroutines.launch
import java.util.Locale

class VideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoBinding
    private var userSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buttonPlayPause.setOnClickListener { musicLoopApp().playerManager.playPause() }
        binding.buttonClose.setOnClickListener { finish() }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                val duration = musicLoopApp().playerManager.state.value.durationMs
                val position = if (duration <= 0L) 0L else (duration * (seekBar?.progress ?: 0)) / SEEK_MAX
                musicLoopApp().playerManager.seekTo(position)
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicLoopApp().playerManager.state.collect { render(it) }
            }
        }
        val row = intent.toMediaRow()
        if (row == null) {
            finish()
            return
        }
        musicLoopApp().playerManager.playItem(row)
    }

    override fun onStart() {
        super.onStart()
        binding.playerView.player = musicLoopApp().playerManager.player
    }

    override fun onStop() {
        binding.playerView.player = null
        if (!isChangingConfigurations) {
            musicLoopApp().playerManager.pause()
        }
        super.onStop()
    }

    private fun render(state: PlaybackUiState) {
        binding.titleText.text = state.current?.displayTitle ?: getString(R.string.player_idle)
        binding.positionText.text = formatClock(state.positionMs, state.durationMs)
        binding.buttonPlayPause.text = if (state.status == PlayStatus.PLAYING) {
            getString(R.string.pause)
        } else {
            getString(R.string.play)
        }
        binding.errorText.text = state.errorMessage.orEmpty()
        if (!userSeeking && state.durationMs > 0L) {
            binding.seekBar.progress = ((state.positionMs * SEEK_MAX) / state.durationMs).toInt().coerceIn(0, SEEK_MAX)
        }
        if (state.errorMessage == "USB disconnected" || state.errorMessage == "USB offline") {
            finish()
        }
    }

    private fun musicLoopApp(): MusicLoopApp = application as MusicLoopApp

    private fun formatClock(positionMs: Long, durationMs: Long): String {
        return "${formatMs(positionMs)} / ${formatMs(durationMs)}"
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%d:%02d", total / 60L, total % 60L)
    }

    companion object {
        private const val SEEK_MAX = 1000
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
                scanStatus = ""
            )
        }
    }
}
