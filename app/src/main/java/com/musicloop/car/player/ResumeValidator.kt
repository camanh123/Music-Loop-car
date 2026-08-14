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
     * Clamp to 0 <= position < duration. Invalid or unknown duration starts at 0.
     */
    fun clampPosition(savedMs: Int, durationMs: Int?): Int {
        if (durationMs == null || durationMs <= 0) {
            return 0
        }
        if (savedMs < 0 || savedMs >= durationMs) {
            return 0
        }
        return savedMs
    }

    fun clampPosition(savedMs: Int, durationMs: Long?): Int {
        val duration = durationMs?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        return clampPosition(savedMs, duration)
    }
}
