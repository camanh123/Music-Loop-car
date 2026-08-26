package com.musicloop.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryEmptyStateTest {

    @Test
    fun searchMissDoesNotImplyUsbEmptyOrOffline() {
        assertEquals(
            LibraryEmptyState.Overlay.SEARCH,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.MUSIC,
                query = "night",
                usbOnline = false,
                listEmpty = true
            )
        )
        assertEquals(
            LibraryEmptyState.Overlay.SEARCH,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.FAVORITES,
                query = "xyz",
                usbOnline = true,
                listEmpty = true
            )
        )
    }

    @Test
    fun usbOfflineMusicOrVideoDefersToDiagnosticBanner() {
        assertEquals(
            LibraryEmptyState.Overlay.USB_DIAGNOSTIC,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.MUSIC,
                query = "",
                usbOnline = false,
                listEmpty = true
            )
        )
        assertEquals(
            LibraryEmptyState.Overlay.USB_DIAGNOSTIC,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.VIDEO,
                query = "  ",
                usbOnline = false,
                listEmpty = true
            )
        )
    }

    @Test
    fun favoritesAndPlaylistsKeepTheirOwnEmptyCopy() {
        assertEquals(
            LibraryEmptyState.Overlay.FAVORITES,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.FAVORITES,
                query = "",
                usbOnline = false,
                listEmpty = true
            )
        )
        assertEquals(
            LibraryEmptyState.Overlay.PLAYLISTS,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.PLAYLISTS,
                query = "",
                usbOnline = true,
                listEmpty = true,
                playlistOpen = false
            )
        )
        assertEquals(
            LibraryEmptyState.Overlay.PLAYLIST_ITEMS,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.PLAYLISTS,
                query = "",
                usbOnline = true,
                listEmpty = true,
                playlistOpen = true
            )
        )
    }

    @Test
    fun populatedListHasNoOverlay() {
        assertNull(
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.MUSIC,
                query = "a",
                usbOnline = true,
                listEmpty = false
            )
        )
    }

    @Test
    fun onlineEmptyLibraryUsesMusicOrVideoCopy() {
        assertEquals(
            LibraryEmptyState.Overlay.MUSIC,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.MUSIC,
                query = "",
                usbOnline = true,
                listEmpty = true
            )
        )
        assertEquals(
            LibraryEmptyState.Overlay.VIDEO,
            LibraryEmptyState.listOverlay(
                tab = LibraryTab.VIDEO,
                query = "",
                usbOnline = true,
                listEmpty = true
            )
        )
    }
}
