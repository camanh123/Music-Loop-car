package com.musicloop.car.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AudioTrackDao {
    @Query("SELECT * FROM audio_tracks WHERE volume_identity = :volumeIdentity ORDER BY title COLLATE NOCASE")
    fun tracksForVolume(volumeIdentity: String): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_tracks WHERE volume_identity = :volumeIdentity AND relative_path = :relativePath LIMIT 1")
    fun find(volumeIdentity: String, relativePath: String): AudioTrackEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: AudioTrackEntity): Long

    @Update
    fun update(entity: AudioTrackEntity)

    @Query("DELETE FROM audio_tracks WHERE id IN (:ids)")
    fun removeByIds(ids: List<Long>)
}

@Dao
interface ScanStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: ScanStateEntity)

    @Query("SELECT * FROM scan_state WHERE id = 1 LIMIT 1")
    fun load(): ScanStateEntity?
}
