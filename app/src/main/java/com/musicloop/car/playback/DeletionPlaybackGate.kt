package com.musicloop.car.playback

import com.musicloop.car.library.MediaIdentity

/**
 * Playback hooks for USB deletion. Stop must set STOPPED so a later STATE_ENDED
 * is not treated as natural end-of-track auto-advance.
 */
interface DeletionPlaybackGate {
    fun isCurrent(identity: MediaIdentity): Boolean
    fun isVideoAttached(identity: MediaIdentity): Boolean
    fun releaseForDeletion(identity: MediaIdentity)
    fun reconcileDeleted(identities: List<MediaIdentity>)
}
