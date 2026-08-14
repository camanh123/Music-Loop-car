package com.musicloop.car.player

/**
 * Boot auto-play is a separate setting from auto-start service.
 * Never retries forever. USB reinsert after boot does not auto-play.
 */
object AutoPlayPolicy {

    fun shouldStart(
        autoPlayEnabled: Boolean,
        bootStart: Boolean,
        usbReady: Boolean,
        track: QueueItem?,
        readable: Boolean,
        alreadyAttempted: Boolean
    ): Boolean {
        if (!autoPlayEnabled) return false
        if (!bootStart) return false
        if (alreadyAttempted) return false
        if (!usbReady) return false
        if (track == null) return false
        if (!track.playable) return false
        if (!readable) return false
        return true
    }
}
