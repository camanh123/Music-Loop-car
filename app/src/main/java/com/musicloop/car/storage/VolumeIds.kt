package com.musicloop.car.storage

import java.util.Locale

/**
 * Stable USB volume identity. Root path is runtime-only and must never be the key.
 *
 * Prefer StorageVolume UUID. When the ROM has no UUID, fall back to a single
 * unlabeled token — not a mount-path basename such as USB1.
 */
object VolumeIds {
    const val UNLABELED_USB = "unlabeled-usb"

    fun resolve(uuid: String?): String {
        val normalized = uuid?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return UNLABELED_USB
        }
        return normalized.uppercase(Locale.US)
    }
}
