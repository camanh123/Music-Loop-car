package com.musicloop.car.storage

/**
 * USB access classification.
 *
 * [classify] is observational and never probes [java.io.File.canWrite].
 * It is used by read/scan/library paths and never grants delete permission.
 *
 * [classifyForDelete] is the only write-capability check. Call it from the
 * delete flow after the current live USB root is known.
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
        return when (capability) {
            UsbAccessCapability.OFFLINE -> listOf(LABEL_OFFLINE)
            UsbAccessCapability.READABLE -> listOf(LABEL_READABLE)
            UsbAccessCapability.READ_ONLY -> listOf(LABEL_READABLE, LABEL_READ_ONLY)
            UsbAccessCapability.WRITABLE -> listOf(LABEL_READABLE, LABEL_WRITABLE)
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
    /**
     * Read-path classification. Ignores [VolumeSnapshot.canWrite] and never
     * probes the filesystem for write access. [allowsDelete] is always false.
     *
     * `mounted_ro` is reported as READ_ONLY (still readable) from mount state
     * alone — not from [java.io.File.canWrite].
     */
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
        if (VolumeEligibility.isReadOnlyMount(snapshot.state)) {
            return UsbVolumeAccess(
                capability = UsbAccessCapability.READ_ONLY,
                readable = true,
                writable = false
            )
        }
        return UsbVolumeAccess(
            capability = UsbAccessCapability.READABLE,
            readable = true,
            writable = false
        )
    }

    fun classify(snapshots: List<VolumeSnapshot>, volumeId: String): UsbVolumeAccess {
        if (volumeId.isBlank()) {
            return classify(null)
        }
        val snapshot = snapshots.firstOrNull { it.volumeId == volumeId && it.presentMountedRemovable }
        return classify(snapshot)
    }

    /**
     * Delete-path classification. Probes writability against the current live
     * root only. Probe exceptions or a blank root mean not writable. Does not
     * affect scan eligibility.
     */
    fun classifyForDelete(
        snapshot: VolumeSnapshot?,
        probeWritable: (rootPath: String) -> Boolean
    ): UsbVolumeAccess {
        val observed = classify(snapshot)
        if (!observed.readable) {
            return observed
        }
        if (observed.capability == UsbAccessCapability.READ_ONLY) {
            return observed
        }
        val root = snapshot?.rootPath
        val writable = try {
            !root.isNullOrBlank() && probeWritable(root)
        } catch (_: Exception) {
            false
        }
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

    fun classifyForDelete(
        snapshots: List<VolumeSnapshot>,
        volumeId: String,
        probeWritable: (rootPath: String) -> Boolean
    ): UsbVolumeAccess {
        if (volumeId.isBlank()) {
            return classifyForDelete(null, probeWritable)
        }
        val snapshot = snapshots.firstOrNull { it.volumeId == volumeId && it.presentMountedRemovable }
        return classifyForDelete(snapshot, probeWritable)
    }
}
