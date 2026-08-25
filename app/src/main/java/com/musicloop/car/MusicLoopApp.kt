package com.musicloop.car

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.musicloop.car.database.AppDatabase
import com.musicloop.car.database.LibraryRepository
import com.musicloop.car.database.RoomLibraryRepository
import com.musicloop.car.library.AndroidMetadataReader
import com.musicloop.car.library.LibraryMediaScanner
import com.musicloop.car.playback.Media3PlayerManager
import com.musicloop.car.playback.MediaItemResolver
import com.musicloop.car.storage.UsbStorageManager
import com.musicloop.car.usb.UsbLifecycleController
import com.musicloop.car.usb.UsbMountReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MusicLoopApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: LibraryRepository
        private set

    lateinit var lifecycleController: UsbLifecycleController
        private set

    lateinit var playerManager: Media3PlayerManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mountReceiver = UsbMountReceiver()

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.create(this)
        repository = RoomLibraryRepository(database)
        val storage = UsbStorageManager(this)
        val scanner = LibraryMediaScanner(
            repository = repository,
            metadataReader = AndroidMetadataReader()
        )
        lifecycleController = UsbLifecycleController(
            snapshotVolumes = { storage.snapshotVolumes() },
            scanner = scanner,
            repository = repository,
            scope = applicationScope,
            onForceReleaseUsbResources = { reason ->
                if (this::playerManager.isInitialized) {
                    playerManager.releaseUsbMedia(reason)
                }
            },
            onOnlineVolumesChanged = { ids ->
                if (this::playerManager.isInitialized) {
                    playerManager.onOnlineVolumesChanged(ids)
                }
            }
        )
        playerManager = Media3PlayerManager(
            context = this,
            repository = repository,
            resolver = MediaItemResolver(snapshotVolumes = { storage.snapshotVolumes() }),
            scope = applicationScope
        )
        registerMountReceiver()
        lifecycleController.start()
    }

    private fun registerMountReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_CHECKING)
            addDataScheme("file")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mountReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(mountReceiver, filter)
        }
    }
}
