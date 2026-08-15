package com.musicloop.car.storage

/**
 * Verified StorageVolume view. [rootPath] is a runtime mount location and must
 * not be used as the database identity.
 */
data class VolumeSnapshot(
    val description: String,
    val state: String,
    val removable: Boolean,
    val isPrimary: Boolean,
    val uuid: String?,
    val rootPath: String?,
    val exists: Boolean,
    val isDirectory: Boolean,
    val canRead: Boolean,
    val listFilesNonNull: Boolean,
    val totalSpaceBytes: Long = 0L,
    val freeSpaceBytes: Long = 0L
) {
    val volumeId: String get() = VolumeIds.resolve(uuid)

    val mounted: Boolean get() = VolumeEligibility.isMounted(state)

    val presentMountedRemovable: Boolean get() = VolumeEligibility.isPresentMountedRemovable(this)

    val scannable: Boolean get() = VolumeEligibility.isScannable(this)

    val skipReason: String get() = VolumeEligibility.skipReason(this)
}
