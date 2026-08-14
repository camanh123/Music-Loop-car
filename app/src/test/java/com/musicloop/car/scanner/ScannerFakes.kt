package com.musicloop.car.scanner

class InMemoryTrackRepository : TrackRepository {
    private val items = LinkedHashMap<String, AudioTrack>()
    private var nextId = 1L

    override fun tracksForVolume(volumeIdentity: String): List<AudioTrack> {
        return items.values.filter { it.volumeIdentity == volumeIdentity }
    }

    override fun upsert(track: AudioTrack): AudioTrack {
        val key = key(track.volumeIdentity, track.relativePath)
        val stored = if (track.id == 0L) {
            val existing = items[key]
            if (existing != null) {
                track.copy(id = existing.id, favorite = existing.favorite || track.favorite)
            } else {
                track.copy(id = nextId++)
            }
        } else {
            track
        }
        items[key] = stored
        return stored
    }

    override fun removeConfirmedMissing(
        volumeIdentity: String,
        folderRelative: String,
        presentRelativePaths: Set<String>
    ) {
        val prefix = if (folderRelative.isEmpty()) "" else "$folderRelative/"
        val staleKeys = items.filter { (_, track) ->
            track.volumeIdentity == volumeIdentity &&
                (folderRelative.isEmpty() ||
                    track.relativePath == folderRelative ||
                    track.relativePath.startsWith(prefix)) &&
                track.relativePath !in presentRelativePaths
        }.keys.toList()
        staleKeys.forEach { items.remove(it) }
    }

    private fun key(volume: String, relative: String) = "$volume::$relative"
}

class FakeAudioProbe(
    var files: MutableList<DiscoveredFile> = mutableListOf(),
    var volumePresent: Boolean = true,
    var throwOnList: Boolean = false
) : AudioFileProbe {
    val snapshots = mutableMapOf<String, ArrayDeque<FileSnapshot>>()
    var listCalls = 0

    override fun listAudioFiles(folderAbsolute: String, volumeRoot: String): EnumerationResult {
        listCalls += 1
        if (throwOnList) {
            error("usb vanished during enumerate")
        }
        return EnumerationResult(
            files = files.toList(),
            totalFilesystemEntries = files.size,
            audioCandidates = files.size,
            acceptedAudioFiles = files.size,
            folderAbsolutePath = folderAbsolute
        )
    }

    override fun snapshot(absolutePath: String): FileSnapshot? {
        val queue = snapshots[absolutePath]
        if (queue != null && queue.isNotEmpty()) {
            return queue.removeFirst()
        }
        val file = files.find { absolutePath.endsWith("/${it.relativePath}") || absolutePath.endsWith(it.relativePath) }
            ?: return FileSnapshot(false, 0, 0)
        return FileSnapshot(true, file.size, file.lastModified)
    }

    override fun isVolumePresent(): Boolean = volumePresent
}

class FakeMetadataReader(
    var resultFor: (String) -> MetadataResult = {
        MetadataResult(title = "Tagged", artist = "Artist", success = true, durationMs = 180_000)
    }
) : MetadataReader {
    val reads = mutableListOf<String>()
    override fun read(absolutePath: String): MetadataResult {
        reads += absolutePath
        return resultFor(absolutePath)
    }
}
