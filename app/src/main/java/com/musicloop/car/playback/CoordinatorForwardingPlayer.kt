package com.musicloop.car.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Routes MediaSession transport to [PlaybackCoordinator] so notification
 * Previous/Next resolve USB paths instead of walking an ExoPlayer playlist.
 * The wrapped ExoPlayer instance is unchanged for Video PlayerView.
 *
 * In-app MP3 next/previous still wrap via [PlaybackCoordinator.next]. Notification
 * skip stops at the first and last queue items and does not advertise unavailable
 * commands.
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

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            coordinator.resume()
        } else {
            coordinator.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        coordinator.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        coordinator.seekTo(positionMs)
    }

    override fun seekToNext() {
        coordinator.skipToNext()
    }

    override fun seekToPrevious() {
        coordinator.skipToPrevious()
    }

    override fun seekToNextMediaItem() {
        coordinator.skipToNext()
    }

    override fun seekToPreviousMediaItem() {
        coordinator.skipToPrevious()
    }

    override fun hasNextMediaItem(): Boolean = coordinator.hasNextItem()

    override fun hasPreviousMediaItem(): Boolean = coordinator.hasPreviousItem()

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> coordinator.hasNextItem()
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> coordinator.hasPreviousItem()
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        val builder = super.getAvailableCommands().buildUpon()
            .remove(COMMAND_SEEK_TO_NEXT)
            .remove(COMMAND_SEEK_TO_PREVIOUS)
            .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        if (coordinator.hasNextItem()) {
            builder.add(COMMAND_SEEK_TO_NEXT)
            builder.add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (coordinator.hasPreviousItem()) {
            builder.add(COMMAND_SEEK_TO_PREVIOUS)
            builder.add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        return builder.build()
    }
}
