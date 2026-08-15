package com.musicloop.car.storage

/**
 * Production library scan limits. Phase 1 [ScanPolicy] stays at depth 3 / 50 files
 * for the USB capability PoC and must not be reused as a library cap.
 */
object LibraryScanPolicy {
    const val MAX_DEPTH = 12
    const val MAX_FILES = 100_000
    const val BATCH_SIZE = 100
    const val UI_LIST_LIMIT = 500
}
