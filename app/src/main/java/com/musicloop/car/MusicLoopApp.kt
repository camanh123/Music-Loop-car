package com.musicloop.car

import android.app.Application
import com.musicloop.car.database.AppDatabase

/**
 * Process entry point. Database lives in internal app storage only.
 */
class MusicLoopApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.create(this)
    }
}
