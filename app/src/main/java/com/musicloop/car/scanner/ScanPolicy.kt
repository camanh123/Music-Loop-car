package com.musicloop.car.scanner

enum class ScanState {
    DISCOVERED,
    WAITING_STABLE,
    VERIFYING,
    READY,
    UNPLAYABLE,
    ERROR
}

enum class MetadataState {
    METADATA_PENDING,
    READY,
    UNVERIFIED,
    UNPLAYABLE,
    ERROR
}

enum class PlayableState {
    UNKNOWN,
    PLAYABLE,
    UNPLAYABLE
}

enum class ScanPhase {
    IDLE,
    ENUMERATING,
    CHECKING,
    COMPLETE,
    INTERRUPTED
}

/**
 * Retry / stability policy for USB scanning.
 *
 * - Newly discovered or size/mtime-changed files enter WAITING_STABLE.
 * - Size is sampled, the scanner waits [STABILITY_WAIT_MS], then samples again.
 * - If size or mtime changed, the file is still being written: stay WAITING_STABLE,
 *   do not read metadata, and never discard the file.
 * - Metadata extraction is enrichment only. Failure must still index a visible track
 *   (READY + UNVERIFIED) and must not mark the track permanently UNPLAYABLE.
 * - Playback capability is decided later by MediaPlayer, not by metadata tags.
 * - USB disappearance or scan cancel: INTERRUPTED; Room is not pruned.
 * - An unreadable child directory is logged and skipped; the rest of the tree is scanned.
 */
object ScanPolicy {
    const val STABILITY_WAIT_MS = 750L
    const val MAX_STABLE_FAILURES = 3
    const val MAX_DIRECTORY_DEPTH = 12
}
