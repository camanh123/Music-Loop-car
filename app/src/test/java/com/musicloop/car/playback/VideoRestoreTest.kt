package com.musicloop.car.playback

import com.musicloop.car.library.MediaListRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRestoreTest {
    @Test
    fun restoresSavedVideoByVolumeIdAndRelativePath() {
        val items = videos("01.mp4", "55.mp4", "56.mp4")
        val saved = VideoPlaybackState(volumeId = "VOL-A", relativePath = "clips/55.mp4")
        val restored = VideoRestore.resolveCurrent(items, saved)!!
        assertTrue(restored.matchedSaved)
        assertEquals("55.mp4", restored.item.fileName)
        assertEquals(1, restored.index)
    }

    @Test
    fun fallsBackToFirstVideoWhenSavedFileIsMissing() {
        val items = videos("01.mp4", "02.mp4")
        val saved = VideoPlaybackState(volumeId = "VOL-A", relativePath = "clips/55.mp4")
        val restored = VideoRestore.resolveCurrent(items, saved)!!
        assertFalse(restored.matchedSaved)
        assertEquals("01.mp4", restored.item.fileName)
        assertEquals(0, restored.index)
    }

    @Test
    fun returnsNullWhenLibraryIsEmpty() {
        val saved = VideoPlaybackState(volumeId = "VOL-A", relativePath = "clips/55.mp4")
        assertNull(VideoRestore.resolveCurrent(emptyList(), saved))
    }

    @Test
    fun previousAndNextUseExistingOrderWithoutWrapping() {
        val items = videos("09.mp4", "10.mp4", "11.mp4")
        val current = items[1]
        assertEquals("11.mp4", VideoRestore.adjacent(items, current, 1)?.fileName)
        assertEquals("09.mp4", VideoRestore.adjacent(items, current, -1)?.fileName)
        assertNull(VideoRestore.adjacent(items, items.first(), -1))
        assertNull(VideoRestore.adjacent(items, items.last(), 1))
    }

    @Test
    fun clampsScrollToRenderedRange() {
        assertEquals(0, VideoRestore.clampScroll(55, 0))
        assertEquals(0, VideoRestore.clampScroll(-3, 10))
        assertEquals(9, VideoRestore.clampScroll(55, 10))
        assertEquals(4, VideoRestore.clampScroll(4, 10))
    }

    @Test
    fun usbReconnectRestoreFindsIdentityThenClampsScroll() {
        val items = (1..60).map { index ->
            MediaListRow(
                id = index.toLong(),
                volumeId = "VOL-A",
                relativePath = "clips/Video_${index.toString().padStart(2, '0')}.mp4",
                fileName = "Video_${index.toString().padStart(2, '0')}.mp4",
                extension = "mp4",
                mediaType = "VIDEO",
                sizeBytes = 1L,
                durationMs = 12_400L,
                title = "Video ${index.toString().padStart(2, '0')}",
                artist = null,
                album = null,
                scanStatus = "READY"
            )
        }
        val saved = VideoPlaybackState(
            volumeId = "VOL-A",
            relativePath = "clips/Video_55.mp4",
            positionMs = 207_000L,
            listPosition = 54,
            listOffset = 12
        )
        val restored = VideoRestore.resolveCurrent(items, saved)!!
        assertTrue(restored.matchedSaved)
        assertEquals("Video_55.mp4", restored.item.fileName)
        assertEquals(54, restored.index)
        assertEquals(54, VideoRestore.clampScroll(saved.listPosition, items.size))
        assertEquals(207_000L, saved.positionMs)
    }

    private fun videos(vararg names: String): List<MediaListRow> {
        return names.mapIndexed { index, name ->
            MediaListRow(
                id = index.toLong(),
                volumeId = "VOL-A",
                relativePath = "clips/$name",
                fileName = name,
                extension = "mp4",
                mediaType = "VIDEO",
                sizeBytes = 1L,
                durationMs = 1_000L,
                title = name,
                artist = null,
                album = null,
                scanStatus = "READY"
            )
        }
    }
}
