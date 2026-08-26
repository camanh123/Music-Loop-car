package com.musicloop.car.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Internal-app library database. Never created on USB storage.
 */
@Database(
    entities = [
        UsbVolumeEntity::class,
        MediaItemEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usbVolumeDao(): UsbVolumeDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao

    companion object {
        const val NAME = "music_loop_library.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorites (
                        volumeId TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        title TEXT,
                        artist TEXT,
                        album TEXT,
                        mediaType TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(volumeId, relativePath)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_items (
                        playlistId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        volumeId TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        title TEXT,
                        artist TEXT,
                        album TEXT,
                        mediaType TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, volumeId, relativePath)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playlist_items_playlistId ON playlist_items(playlistId)"
                )
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
