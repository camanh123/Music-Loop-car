package com.musicloop.car.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.musicloop.car.databinding.ActivitySettingsBinding
import com.musicloop.car.player.PlaybackStateStore
import com.musicloop.car.player.RepeatMode
import com.musicloop.car.settings.AppSettingsStore
import com.musicloop.car.storage.MusicFolderStore
import com.musicloop.car.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val settings = AppSettingsStore(this)
        val playback = PlaybackStateStore(this)
        val folder = MusicFolderStore(this).load()

        binding.switchAutoStart.isChecked = settings.autoStartService()
        binding.switchAutoPlay.isChecked = settings.autoPlayOnBoot()
        binding.switchAutoStart.setOnCheckedChangeListener { _, checked ->
            settings.setAutoStartService(checked)
        }
        binding.switchAutoPlay.setOnCheckedChangeListener { _, checked ->
            settings.setAutoPlayOnBoot(checked)
        }
        binding.settingsRepeatValue.text = getString(
            R.string.settings_repeat
        ) + ": " + when (playback.loadRepeatMode()) {
            RepeatMode.OFF -> getString(R.string.action_repeat)
            RepeatMode.ALL -> getString(R.string.action_repeat_all)
            RepeatMode.ONE -> getString(R.string.action_repeat_one)
        }
        binding.settingsShuffleValue.text = getString(R.string.settings_shuffle) + ": " +
            if (playback.loadShuffle()) getString(R.string.action_shuffle_on) else getString(R.string.action_shuffle)
        binding.settingsFolderValue.text = folder?.displayLabel() ?: getString(R.string.music_folder_placeholder)
    }
}
