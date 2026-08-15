package com.musicloop.car.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Internal-app library database. Never created on USB storage.
 */
@Database(
    entities = [UsbVolumeEntity::class, MediaItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usbVolumeDao(): UsbVolumeDao
    abstract fun mediaItemDao(): MediaItemDao

    companion object {
        const val NAME = "music_loop_library.db"

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME
            ).build()
        }
    }
}
