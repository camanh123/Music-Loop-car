package com.musicloop.car.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database stored in internal app storage via Context.getDatabasePath.
 * Never created on USB. Version 2 adds internal playlists without destroying tracks.
 */
@Database(
    entities = [
        AudioTrackEntity::class,
        ScanStateEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun scanStateDao(): ScanStateDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "musicloop.db"

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
