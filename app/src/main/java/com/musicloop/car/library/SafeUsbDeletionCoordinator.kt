package com.musicloop.car.library

import com.musicloop.car.database.MediaItemEntity
import com.musicloop.car.playback.DeletionPlaybackGate
import com.musicloop.car.playback.MediaItemResolver
import com.musicloop.car.playback.MediaPaths
import com.musicloop.car.playback.ResolveResult
import com.musicloop.car.storage.MediaExtensions
import com.musicloop.car.storage.UsbAccess
import com.musicloop.car.storage.UsbAccessCapability
import com.musicloop.car.storage.UsbFileSystem
import com.musicloop.car.storage.VolumeSnapshot
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Safe USB media deletion. Identity is always volumeId + relativePath resolved
 * against the current StorageManager snapshot. Cached mount locations and
 * stale absolute paths are never used as identity.
 */
class SafeUsbDeletionCoordinator(
    private val snapshotVolumes: () -> List<VolumeSnapshot>,
    private val libraryLookup: suspend (String, String) -> MediaItemEntity?,
    private val removeFromLibrary: suspend (String, String) -> Unit,
    private val playback: DeletionPlaybackGate,
    private val fs: UsbFileSystem,
    private val resolver: MediaItemResolver = MediaItemResolver(snapshotVolumes)
) {
    private val deleting = AtomicBoolean(false)

    val isDeleting: Boolean get() = deleting.get()

    suspend fun deleteAll(
        identities: List<MediaIdentity>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchDeleteResult {
        val unique = identities.distinct()
        if (unique.isEmpty()) {
            return BatchDeleteResult()
        }
        if (!deleting.compareAndSet(false, true)) {
            return BatchDeleteResult(
                failed = unique.map { FailedDelete(it, DeleteFailureReason.DUPLICATE_IN_PROGRESS) },
                duplicateBlocked = true
            )
        }
        val succeeded = mutableListOf<MediaIdentity>()
        val failed = mutableListOf<FailedDelete>()
        try {
            unique.forEachIndexed { index, identity ->
                onProgress(index + 1, unique.size)
                when (val outcome = deleteOne(identity)) {
                    is ItemOutcome.Success -> succeeded += identity
                    is ItemOutcome.Failure -> failed += FailedDelete(identity, outcome.reason)
                }
            }
            if (succeeded.isNotEmpty()) {
                playback.reconcileDeleted(succeeded)
            }
        } finally {
            deleting.set(false)
        }
        return BatchDeleteResult(succeeded = succeeded, failed = failed)
    }

    private suspend fun deleteOne(identity: MediaIdentity): ItemOutcome {
        val volumeId = identity.volumeId.trim()
        val relativePath = identity.relativePath.trim()
        if (volumeId.isBlank() || relativePath.isBlank()) {
            return ItemOutcome.Failure(DeleteFailureReason.INVALID_IDENTITY)
        }
        if (MediaPaths.hasTraversal(relativePath)) {
            return ItemOutcome.Failure(DeleteFailureReason.PATH_ESCAPE)
        }
        val kind = MediaExtensions.kindOf(relativePath.substringAfterLast('/').ifBlank { relativePath })
            ?: return ItemOutcome.Failure(DeleteFailureReason.UNSUPPORTED_MEDIA)

        val snapshots = try {
            snapshotVolumes()
        } catch (_: Exception) {
            emptyList()
        }
        val access = UsbAccess.classify(snapshots, volumeId)
        if (!access.readable) {
            return ItemOutcome.Failure(DeleteFailureReason.OFFLINE)
        }
        if (access.capability == UsbAccessCapability.READ_ONLY) {
            return ItemOutcome.Failure(DeleteFailureReason.READ_ONLY)
        }

        val entity = try {
            libraryLookup(volumeId, relativePath)
        } catch (_: Exception) {
            null
        } ?: return ItemOutcome.Failure(DeleteFailureReason.NOT_IN_LIBRARY)
        val expectedType = if (kind.name == "VIDEO") "VIDEO" else "AUDIO"
        if (entity.mediaType != expectedType) {
            return ItemOutcome.Failure(DeleteFailureReason.UNSUPPORTED_MEDIA)
        }

        val snapshot = snapshots.firstOrNull { it.volumeId == volumeId && it.presentMountedRemovable }
            ?: return ItemOutcome.Failure(DeleteFailureReason.OFFLINE)
        val currentRoot = snapshot.rootPath
        if (currentRoot.isNullOrBlank()) {
            return ItemOutcome.Failure(DeleteFailureReason.OFFLINE)
        }

        val resolved = try {
            resolver.resolve(volumeId, relativePath)
        } catch (_: Exception) {
            ResolveResult.Invalid("resolve failed")
        }
        val ready = resolved as? ResolveResult.Ready
            ?: return when (resolved) {
                is ResolveResult.Offline -> ItemOutcome.Failure(DeleteFailureReason.OFFLINE)
                is ResolveResult.Missing -> ItemOutcome.Failure(DeleteFailureReason.MISSING)
                is ResolveResult.Unsupported -> ItemOutcome.Failure(DeleteFailureReason.UNSUPPORTED_MEDIA)
                is ResolveResult.Invalid -> ItemOutcome.Failure(DeleteFailureReason.INVALID_IDENTITY)
                is ResolveResult.Ready -> ItemOutcome.Failure(DeleteFailureReason.INVALID_IDENTITY)
            }
        if (ready.rootPath != currentRoot) {
            return ItemOutcome.Failure(DeleteFailureReason.MOUNT_CHANGED)
        }
        val absolute = ready.absolutePath
        if (!MediaPaths.isContained(currentRoot, absolute)) {
            return ItemOutcome.Failure(DeleteFailureReason.PATH_ESCAPE)
        }
        val rootCanonical = fs.canonicalPath(currentRoot)
        val fileCanonical = fs.canonicalPath(absolute)
        if (rootCanonical != null && fileCanonical != null) {
            if (!MediaPaths.isContained(rootCanonical, fileCanonical) && fileCanonical != rootCanonical) {
                return ItemOutcome.Failure(DeleteFailureReason.PATH_ESCAPE)
            }
            if (fileCanonical == rootCanonical) {
                return ItemOutcome.Failure(DeleteFailureReason.NOT_REGULAR_FILE)
            }
        }
        if (!fs.exists(absolute)) {
            return ItemOutcome.Failure(DeleteFailureReason.MISSING)
        }
        if (!fs.isRegularFile(absolute)) {
            return ItemOutcome.Failure(DeleteFailureReason.NOT_REGULAR_FILE)
        }
        val writeAccess = UsbAccess.classifyForDelete(snapshot) { root -> fs.canWrite(root) }
        if (!writeAccess.allowsDelete) {
            return ItemOutcome.Failure(DeleteFailureReason.READ_ONLY)
        }
        if (!fs.canWrite(absolute)) {
            return ItemOutcome.Failure(DeleteFailureReason.READ_ONLY)
        }

        val snapshotsBeforeDelete = try {
            snapshotVolumes()
        } catch (_: Exception) {
            emptyList()
        }
        val stillSame = snapshotsBeforeDelete.firstOrNull {
            it.volumeId == volumeId && it.presentMountedRemovable && it.rootPath == currentRoot
        }
        if (stillSame == null) {
            return ItemOutcome.Failure(DeleteFailureReason.MOUNT_CHANGED)
        }
        val accessBeforeDelete = UsbAccess.classifyForDelete(stillSame) { root -> fs.canWrite(root) }
        if (!accessBeforeDelete.allowsDelete) {
            return ItemOutcome.Failure(
                if (!accessBeforeDelete.readable) DeleteFailureReason.OFFLINE else DeleteFailureReason.READ_ONLY
            )
        }

        if (playback.isCurrent(identity) || playback.isVideoAttached(identity)) {
            try {
                playback.releaseForDeletion(identity)
            } catch (_: Exception) {
                return ItemOutcome.Failure(DeleteFailureReason.FILESYSTEM_REFUSED)
            }
        }

        val deleted = try {
            fs.deleteRegularFile(absolute)
        } catch (_: Exception) {
            false
        }
        if (!deleted || fs.exists(absolute)) {
            return ItemOutcome.Failure(
                if (!fs.canWrite(absolute)) {
                    DeleteFailureReason.PERMISSION_DENIED
                } else {
                    DeleteFailureReason.FILESYSTEM_REFUSED
                }
            )
        }

        try {
            removeFromLibrary(volumeId, relativePath)
        } catch (_: Exception) {
            // Filesystem already deleted. Library reconcile failure must not
            // invent a second delete, and must not report FS failure.
        }
        return ItemOutcome.Success
    }

    private sealed class ItemOutcome {
        object Success : ItemOutcome()
        data class Failure(val reason: DeleteFailureReason) : ItemOutcome()
    }
}
