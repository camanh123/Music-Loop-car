package com.musicloop.car.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryUiStoreTest {

    @Test
    fun tabPersistenceRoundTrip() {
        assertEquals(LibraryTab.VIDEO, LibraryUiStore.tabFrom("VIDEO"))
        assertEquals(LibraryTab.FAVORITES, LibraryUiStore.tabFrom("FAVORITES"))
        assertEquals(LibraryTab.PLAYLISTS, LibraryUiStore.tabFrom("PLAYLISTS"))
        assertEquals(LibraryTab.MUSIC, LibraryUiStore.tabFrom(null))
        assertEquals(LibraryTab.MUSIC, LibraryUiStore.tabFrom("nope"))
    }

    @Test
    fun sortPersistenceRoundTripIndependentKeys() {
        assertEquals(LibrarySort.Z_A, LibraryUiStore.sortFrom("Z_A"))
        assertEquals(LibrarySort.NEWEST, LibraryUiStore.sortFrom("NEWEST"))
        assertEquals(LibrarySort.OLDEST, LibraryUiStore.sortFrom("OLDEST"))
        assertEquals(LibrarySort.ARTIST, LibraryUiStore.sortFrom("ARTIST"))
        assertEquals(LibrarySort.ALBUM, LibraryUiStore.sortFrom("ALBUM"))
        assertEquals(LibrarySort.A_Z, LibraryUiStore.sortFrom(null))
        assertEquals(LibrarySort.A_Z, LibraryUiStore.sortFrom("unknown"))
    }
}
