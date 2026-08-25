package com.musicloop.car.library

import android.content.Context

/**
 * Library chrome persistence. Separate from [com.musicloop.car.playback.VideoPlaybackStore]
 * so video identity/scroll/position stay untouched.
 */
class LibraryUiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): LibraryUiSnapshot {
        return LibraryUiSnapshot(
            tab = tabFrom(prefs.getString(KEY_TAB, LibraryTab.MUSIC.name)),
            musicSort = sortFrom(prefs.getString(KEY_MUSIC_SORT, LibrarySort.A_Z.name)),
            videoSort = sortFrom(prefs.getString(KEY_VIDEO_SORT, LibrarySort.A_Z.name)),
            query = prefs.getString(KEY_QUERY, "").orEmpty()
        )
    }

    fun saveTab(tab: LibraryTab) {
        prefs.edit().putString(KEY_TAB, tab.name).apply()
    }

    fun saveSort(tab: LibraryTab, sort: LibrarySort) {
        val key = if (tab == LibraryTab.MUSIC) KEY_MUSIC_SORT else KEY_VIDEO_SORT
        prefs.edit().putString(key, sort.name).apply()
    }

    fun saveQuery(query: String) {
        prefs.edit().putString(KEY_QUERY, query).apply()
    }

    companion object {
        private const val PREFS_NAME = "library_ui_state"
        private const val KEY_TAB = "tab"
        private const val KEY_MUSIC_SORT = "music_sort"
        private const val KEY_VIDEO_SORT = "video_sort"
        private const val KEY_QUERY = "query"

        fun tabFrom(raw: String?): LibraryTab {
            return raw?.let { value ->
                runCatching { LibraryTab.valueOf(value) }.getOrNull()
            } ?: LibraryTab.MUSIC
        }

        fun sortFrom(raw: String?): LibrarySort {
            return raw?.let { value ->
                runCatching { LibrarySort.valueOf(value) }.getOrNull()
            } ?: LibrarySort.A_Z
        }
    }
}

data class LibraryUiSnapshot(
    val tab: LibraryTab = LibraryTab.MUSIC,
    val musicSort: LibrarySort = LibrarySort.A_Z,
    val videoSort: LibrarySort = LibrarySort.A_Z,
    val query: String = ""
)
