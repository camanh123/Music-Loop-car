package com.musicloop.car.player

/**
 * Safe resume rules. Never blindly seek to a stale position.
 */
object ResumeValidator {

    fun sameTrack(
        savedVolumeIdentity: String?,
        savedRelativePath: String?,
        currentVolumeIdentity: String?,
        currentRelativePath: String?
    ): Boolean {
        if (savedVolumeIdentity.isNullOrBlank() || savedRelativePath.isNullOrBlank()) {
            return false
        }
        if (currentVolumeIdentity.isNullOrBlank() || currentRelativePath.isNullOrBlank()) {
            return false
        }
        return savedVolumeIdentity == currentVolumeIdentity &&
            savedRelativePath == currentRelativePath
    }

    /**
     * When duration is known: clamp to 0 <= position < duration.
     * When duration is unknown: keep a non-negative saved position for later clamping.
     */
    fun clampPosition(savedMs: Int, durationMs: Int?): Int {
        if (savedMs < 0) {
            return 0
        }
        if (durationMs == null || durationMs <= 0) {
            return savedMs
        }
        if (savedMs >= durationMs) {
            return 0
        }
        return savedMs
    }
}
