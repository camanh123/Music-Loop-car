package com.musicloop.car.scanner

interface AudioFileProbe {
    fun listAudioFiles(folderAbsolute: String, volumeRoot: String): EnumerationResult
    fun snapshot(absolutePath: String): FileSnapshot?
    fun isVolumePresent(): Boolean
}

interface MetadataReader {
    fun read(absolutePath: String): MetadataResult
}

interface TrackRepository {
    fun tracksForVolume(volumeIdentity: String): List<AudioTrack>
    fun upsert(track: AudioTrack): AudioTrack
    fun removeConfirmedMissing(
        volumeIdentity: String,
        folderRelative: String,
        presentRelativePaths: Set<String>
    )
}

fun interface ScanClock {
    fun now(): Long
}

fun interface ScanSleeper {
    fun sleep(durationMs: Long)
}
