package com.musicloop.car.library

/**
 * List overlay copy. USB host logic stays in UsbLifecycleController;
 * this only chooses what the empty list should say.
 */
object LibraryEmptyState {
    enum class Overlay {
        SEARCH,
        MUSIC,
        VIDEO,
        FAVORITES,
        PLAYLISTS,
        PLAYLIST_ITEMS,
        USB_DIAGNOSTIC
    }

    fun listOverlay(
        tab: LibraryTab,
        query: String,
        usbOnline: Boolean,
        listEmpty: Boolean,
        playlistOpen: Boolean = false
    ): Overlay? {
        if (!listEmpty) {
            return null
        }
        if (query.isNotBlank()) {
            return Overlay.SEARCH
        }
        return when (tab) {
            LibraryTab.FAVORITES -> Overlay.FAVORITES
            LibraryTab.PLAYLISTS -> if (playlistOpen) Overlay.PLAYLIST_ITEMS else Overlay.PLAYLISTS
            LibraryTab.MUSIC, LibraryTab.VIDEO -> {
                if (!usbOnline) {
                    Overlay.USB_DIAGNOSTIC
                } else if (tab == LibraryTab.VIDEO) {
                    Overlay.VIDEO
                } else {
                    Overlay.MUSIC
                }
            }
        }
    }
}
