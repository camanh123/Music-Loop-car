package com.musicloop.car.storage

/**
 * Non-destructive USB access classification.
 *
 * Write capability uses StorageVolume mount state plus [File.canWrite] on the
 * current root. Never creates, writes, or deletes probe files on user media.
 */
enum class UsbAccessCapability {
    OFFLINE,
    READABLE,
    READ_ONLY,
    WRITABLE
}

data class UsbVolumeAccess(
    val capability: UsbAccessCapability,
    val readable: Boolean,
    val writable: Boolean
) {
    val allowsDelete: Boolean get() = capability == UsbAccessCapability.WRITABLE && writable

    val label: String
        get() = when (capability) {
            UsbAccessCapability.OFFLINE -> LABEL_OFFLINE
            UsbAccessCapability.READABLE -> LABEL_READABLE
            UsbAccessCapability.READ_ONLY -> LABEL_READ_ONLY
            UsbAccessCapability.WRITABLE -> LABEL_WRITABLE
        }

    fun statusLabels(): List<String> {
        if (!readable) {
            return listOf(LABEL_OFFLINE)
        }
        return buildList {
            add(LABEL_READABLE)
            add(if (writable) LABEL_WRITABLE else LABEL_READ_ONLY)
        }
    }

    companion object {
        const val LABEL_OFFLINE = "USB OFFLINE"
        const val LABEL_READABLE = "USB READABLE"
        const val LABEL_READ_ONLY = "USB READ-ONLY"
        const val LABEL_WRITABLE = "USB WRITABLE"
    }
}

object UsbAccess {
    fun classify(snapshot: VolumeSnapshot?): UsbVolumeAccess {
        if (snapshot == null || !snapshot.presentMountedRemovable) {
            return UsbVolumeAccess(
                capability = UsbAccessCapability.OFFLINE,
                readable = false,
                writable = false
            )
        }
        val root = snapshot.rootPath
        val readable = !root.isNullOrBlank() &&
            snapshot.exists &&
            snapshot.isDirectory &&
            snapshot.canRead
        if (!readable) {
            return UsbVolumeAccess(
                capability = UsbAccessCapability.OFFLINE,
                readable = false,
                writable = false
            )
        }
        val writable = snapshot.canWrite && !VolumeEligibility.isReadOnlyMount(snapshot.state)
        return if (writable) {
            UsbVolumeAccess(
                capability = UsbAccessCapability.WRITABLE,
                readable = true,
                writable = true
            )
        } else {
            UsbVolumeAccess(
                capability = UsbAccessCapability.READ_ONLY,
                readable = true,
                writable = false
            )
        }
    }

    fun classify(snapshots: List<VolumeSnapshot>, volumeId: String): UsbVolumeAccess {
        if (volumeId.isBlank()) {
            return classify(null)
        }
        val snapshot = snapshots.firstOrNull { it.volumeId == volumeId && it.presentMountedRemovable }
        return classify(snapshot)
    }
}
