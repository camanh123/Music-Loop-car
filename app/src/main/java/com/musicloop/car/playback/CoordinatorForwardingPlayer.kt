package com.musicloop.car.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Routes MediaSession transport to [PlaybackCoordinator] so notification
 * Previous/Next resolve USB paths instead of walking an ExoPlayer playlist.
 * The wrapped ExoPlayer instance is unchanged for Video PlayerView.
 */
@UnstableApi
class CoordinatorForwardingPlayer(
    player: Player,
    private val coordinator: PlaybackCoordinator
) : ForwardingPlayer(player) {

    override fun play() {
        coordinator.resume()
    }

    override fun pause() {
        coordinator.pause()
    }

    override fun seekTo(positionMs: Long) {
        coordinator.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        coordinator.seekTo(positionMs)
    }

    override fun seekToNext() {
        coordinator.next()
    }

    override fun seekToPrevious() {
        coordinator.previous()
    }

    override fun seekToNextMediaItem() {
        coordinator.next()
    }

    override fun seekToPreviousMediaItem() {
        coordinator.previous()
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }
}
