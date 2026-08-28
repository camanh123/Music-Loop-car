package com.musicloop.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteMessagesTest {

    @Test
    fun singleConfirmationNamesFileAndIsPermanent() {
        val body = DeleteMessages.singleBody("Summer Night", "Summer_Night.mp3", currentlyPlaying = false, video = false)
        assertTrue(body.contains("Tên bài:"))
        assertTrue(body.contains("Summer Night"))
        assertTrue(body.contains("Summer_Night.mp3"))
        assertTrue(body.contains("vĩnh viễn"))
        assertFalse(body.contains("Delete?"))
        assertFalse(body.contains("đang được phát"))
    }

    @Test
    fun currentlyPlayingAddsStopWarning() {
        val body = DeleteMessages.singleBody("Clip", "clip.mp4", currentlyPlaying = true, video = true)
        assertTrue(body.contains("Tên:"))
        assertTrue(body.contains("Tệp này đang được phát. Phát lại sẽ dừng trước khi xóa."))
    }

    @Test
    fun batchConfirmationReportsCurrentTabTypeAndSize() {
        val music = DeleteMessages.batchBody(7, 0, 438L * 1024L * 1024L)
        assertTrue(music.contains("7 bài hát"))
        assertFalse(music.contains("video"))
        assertTrue(music.contains("438 MB"))
        val mixed = DeleteMessages.batchBody(5, 2, 10L * 1024L * 1024L)
        assertTrue(mixed.contains("5 bài hát"))
        assertTrue(mixed.contains("2 video"))
    }

    @Test
    fun fileInfoUsesRelativePathNotMountRoot() {
        val row = MediaListRow(
            id = 1L,
            volumeId = "AAAA-AAAA",
            relativePath = "Music/Summer_Night.mp3",
            fileName = "Summer_Night.mp3",
            extension = "mp3",
            mediaType = "AUDIO",
            sizeBytes = 2048L,
            durationMs = 90_000L,
            title = "Summer Night",
            artist = "Band",
            album = null,
            scanStatus = "COMPLETE"
        )
        val text = MediaFileInfo.format(row)
        assertTrue(text.contains("Summer Night"))
        assertTrue(text.contains("Band"))
        assertTrue(text.contains("Music/Summer_Night.mp3"))
        assertFalse(text.contains("/mnt/media_rw"))
        assertFalse(text.contains("/storage/"))
    }
}
