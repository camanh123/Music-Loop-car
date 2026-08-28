package com.musicloop.car.library

/**
 * Multi-select for MUSIC/VIDEO USB rows. Selections never mix media types.
 */
data class MediaSelectionState(
    val active: Boolean = false,
    val tab: LibraryTab = LibraryTab.MUSIC,
    val selected: Set<MediaIdentity> = emptySet()
) {
    val count: Int get() = selected.size

    fun contains(row: MediaListRow): Boolean {
        return selected.contains(row.identity())
    }
}

object MediaSelection {
    fun canSelect(tab: LibraryTab, row: MediaListRow): Boolean {
        if (tab != LibraryTab.MUSIC && tab != LibraryTab.VIDEO) {
            return false
        }
        if (!row.available) {
            return false
        }
        return row.mediaType == LibraryListQuery.mediaType(tab)
    }

    fun enter(tab: LibraryTab, first: MediaListRow): MediaSelectionState {
        if (!canSelect(tab, first)) {
            return MediaSelectionState()
        }
        return MediaSelectionState(
            active = true,
            tab = tab,
            selected = setOf(first.identity())
        )
    }

    fun toggle(state: MediaSelectionState, row: MediaListRow): MediaSelectionState {
        if (!state.active || !canSelect(state.tab, row)) {
            return state
        }
        val identity = row.identity()
        val next = if (identity in state.selected) {
            state.selected - identity
        } else {
            state.selected + identity
        }
        return state.copy(selected = next)
    }

    fun selectAll(state: MediaSelectionState, visible: List<MediaListRow>): MediaSelectionState {
        if (!state.active) {
            return state
        }
        val identities = visible
            .filter { canSelect(state.tab, it) }
            .map { it.identity() }
            .toSet()
        return state.copy(selected = identities)
    }

    fun rowsForDeletion(
        state: MediaSelectionState,
        visible: List<MediaListRow>
    ): List<MediaListRow> {
        if (!state.active) {
            return emptyList()
        }
        val wanted = state.selected
        return visible.filter { canSelect(state.tab, it) && it.identity() in wanted }
    }

    fun exit(): MediaSelectionState = MediaSelectionState()
}
