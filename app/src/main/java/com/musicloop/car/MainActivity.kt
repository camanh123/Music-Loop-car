package com.musicloop.car

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.musicloop.car.databinding.ActivityMainBinding

/**
 * Landscape automotive shell for 1280x720 CARFU displays.
 * Playback, scanning, and USB discovery are intentionally not wired in Phase 1.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderPlaceholderState()
    }

    private fun renderPlaceholderState() {
        binding.usbStatusValue.setText(R.string.usb_status_waiting)
        binding.musicCountValue.setText(R.string.music_count_placeholder)
        binding.songTitle.setText(R.string.now_playing_empty_title)
        binding.songArtist.setText(R.string.now_playing_empty_artist)
        binding.songAlbum.setText(R.string.now_playing_empty_album)
        binding.positionText.setText(R.string.playback_position_placeholder)
        binding.durationText.setText(R.string.playback_duration_placeholder)
        binding.seekBar.progress = 0
        binding.seekBar.isEnabled = false
        binding.buttonPrevious.isEnabled = false
        binding.buttonPlayPause.isEnabled = false
        binding.buttonNext.isEnabled = false
        binding.buttonShuffle.isEnabled = false
        binding.buttonRepeat.isEnabled = false
        binding.buttonFavorite.isEnabled = false
    }
}
