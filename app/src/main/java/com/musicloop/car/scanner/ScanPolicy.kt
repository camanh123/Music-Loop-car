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
 * Retry / stability policy for Phase 3.
 *
 * - Newly discovered or size/mtime-changed files enter WAITING_STABLE.
 * - Size is sampled, the scanner waits [STABILITY_WAIT_MS], then samples again.
 * - If size or mtime changed, the file is still being written: stay WAITING_STABLE,
 *   do not read metadata, and never mark UNPLAYABLE.
 * - Metadata/read failures on a *stable* file increment verifyFailures.
 * - After [MAX_STABLE_FAILURES] consecutive stable failures: UNPLAYABLE.
 * - USB disappearance or scan cancel: INTERRUPTED; Room is not pruned.
 */
object ScanPolicy {
    const val STABILITY_WAIT_MS = 750L
    const val MAX_STABLE_FAILURES = 3
    const val MAX_DIRECTORY_DEPTH = 12
}
