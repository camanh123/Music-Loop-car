package com.musicloop.car.playback

import com.musicloop.car.storage.MediaKind
import com.musicloop.car.storage.MediaExtensions
import com.musicloop.car.storage.VolumeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemResolverTest {

    @Test
    fun resolvesVolumeIdPlusRelativePathAgainstRuntimeRoot() {
        val resolver = MediaItemResolver(
            snapshotVolumes = { listOf(online("AAAA-AAAA", "/mnt/media_rw/AAAA-AAAA")) },
            fileReadable = { true }
        )
        val result = resolver.resolve("AAAA-AAAA", "Music/song.mp3") as ResolveResult.Ready
        assertEquals("/mnt/media_rw/AAAA-AAAA/Music/song.mp3", result.absolutePath)
        assertEquals("/mnt/media_rw/AAAA-AAAA", result.rootPath)
    }

    @Test
    fun remountedRootStillResolvesSameIdentity() {
        val resolver = MediaItemResolver(
            snapshotVolumes = { listOf(online("ABCD-EF01", "/mnt/media_rw/new-root")) },
            fileReadable = { true }
        )
        val result = resolver.resolve("ABCD-EF01", "clip.mp4") as ResolveResult.Ready
        assertEquals("/mnt/media_rw/new-root/clip.mp4", result.absolutePath)
    }

    @Test
    fun offlineVolumeDoesNotAttemptPlayback() {
        val resolver = MediaItemResolver(
            snapshotVolumes = { listOf(online("AAAA-AAAA", "/mnt/a").copy(state = "unmounted")) },
            fileReadable = { true }
        )
        val result = resolver.resolve("AAAA-AAAA", "song.mp3")
        assertTrue(result is ResolveResult.Offline)
    }

    @Test
    fun missingVolumeIsOffline() {
        val resolver = MediaItemResolver(
            snapshotVolumes = { emptyList() },
            fileReadable = { true }
        )
        assertTrue(resolver.resolve("AAAA-AAAA", "song.mp3") is ResolveResult.Offline)
    }

    @Test
    fun missingFileIsReported() {
        val resolver = MediaItemResolver(
            snapshotVolumes = { listOf(online("AAAA-AAAA", "/mnt/a")) },
            fileReadable = { false }
        )
        val result = resolver.resolve("AAAA-AAAA", "gone.mp3")
        assertTrue(result is ResolveResult.Missing)
    }

    @Test
    fun unsupportedMediaIsRejected() {
        val resolver = MediaItemResolver(
            snapshotVolumes = { listOf(online("AAAA-AAAA", "/mnt/a")) },
            fileReadable = { true }
        )
        assertTrue(resolver.resolve("AAAA-AAAA", "notes.txt") is ResolveResult.Unsupported)
        assertTrue(resolver.resolve("AAAA-AAAA", "photo.jpg") is ResolveResult.Unsupported)
    }

    @Test
    fun mediaTypeDetectionMatchesWhitelist() {
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("a.MP3"))
        assertEquals(MediaKind.AUDIO, MediaExtensions.kindOf("a.flac"))
        assertEquals(MediaKind.VIDEO, MediaExtensions.kindOf("b.mkv"))
        assertEquals(MediaKind.VIDEO, MediaExtensions.kindOf("b.TS"))
    }

    @Test
    fun fileReadableExceptionBecomesMissing() {
        val resolver = MediaItemResolver(
            snapshotVolumes = { listOf(online("AAAA-AAAA", "/mnt/a")) },
            fileReadable = { throw IllegalStateException("io") }
        )
        assertTrue(resolver.resolve("AAAA-AAAA", "bad.mp3") is ResolveResult.Missing)
    }

    private fun online(uuid: String, root: String): VolumeSnapshot {
        return VolumeSnapshot(
            description = "USB DISK",
            state = "mounted",
            removable = true,
            isPrimary = false,
            uuid = uuid,
            rootPath = root,
            exists = true,
            isDirectory = true,
            canRead = true,
            listFilesNonNull = true
        )
    }
}
