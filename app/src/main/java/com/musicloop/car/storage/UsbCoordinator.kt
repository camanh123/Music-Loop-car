package com.musicloop.car.storage

import com.musicloop.car.ui.state.UsbStatus
import com.musicloop.car.ui.state.UsbUiState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

fun interface MainPoster {
    fun post(block: () -> Unit)
}

/**
 * Coordinates USB discovery, saved-folder restore, and hotplug status.
 * Scanning is owned by MusicScanController.
 */
class UsbCoordinator(
    private val discoverVolumes: () -> List<UsbVolume>,
    private val directoryExists: (String) -> Boolean,
    private val store: MusicFolderRepository,
    private val resolver: MusicFolderResolver,
    private val ioExecutor: Executor,
    private val mainPoster: MainPoster,
    private val listener: (UsbUiState) -> Unit
) {

    private val running = AtomicBoolean(false)
    private var sawUsbInSession = false
    private var lastState: UsbUiState = UsbUiState(UsbStatus.WAITING_FOR_USB)

    fun start() {
        running.set(true)
        refresh()
    }

    fun stop() {
        running.set(false)
    }

    fun refresh() {
        emit(
            lastState.copy(
                status = UsbStatus.SCANNING_USB
            )
        )
        ioExecutor.execute {
            val next = try {
                computeState()
            } catch (_: Exception) {
                UsbUiState(
                    status = UsbStatus.USB_ERROR,
                    hasSavedFolder = store.load() != null,
                    usbPresent = false
                )
            }
            mainPoster.post {
                if (running.get()) {
                    emit(next)
                }
            }
        }
    }

    fun onFolderSelected(record: MusicFolderRecord) {
        store.save(record)
        sawUsbInSession = true
        refresh()
    }

    fun currentState(): UsbUiState = lastState

    private fun computeState(): UsbUiState {
        val volumes = try {
            discoverVolumes()
        } catch (_: Exception) {
            return UsbUiState(status = UsbStatus.USB_ERROR, hasSavedFolder = store.load() != null)
        }
        val saved = store.load()
        val usbPresent = volumes.any { directoryExists(it.absolutePath) }

        if (!usbPresent) {
            val status = if (sawUsbInSession || saved != null) {
                UsbStatus.USB_DISCONNECTED
            } else {
                UsbStatus.WAITING_FOR_USB
            }
            return UsbUiState(
                status = status,
                musicFolderLabel = saved?.displayLabel(),
                hasSavedFolder = saved != null,
                usbPresent = false,
                volumeIdentity = saved?.volumeUuid
            )
        }

        sawUsbInSession = true

        if (saved == null) {
            return UsbUiState(
                status = UsbStatus.NEEDS_FOLDER,
                usbPresent = true,
                hasSavedFolder = false
            )
        }

        return when (val resolved = resolver.resolve(saved, volumes)) {
            is FolderResolveResult.Found -> {
                if (resolved.record != saved) {
                    store.save(resolved.record)
                }
                UsbUiState(
                    status = UsbStatus.USB_READY,
                    musicFolderLabel = resolved.record.displayLabel(),
                    resolvedAbsolutePath = resolved.absolutePath,
                    hasSavedFolder = true,
                    usbPresent = true,
                    volumeIdentity = resolved.volume.stableIdentity(),
                    volumeRootPath = resolved.volume.absolutePath
                )
            }
            FolderResolveResult.WaitingForUsb -> UsbUiState(
                status = if (usbPresent) UsbStatus.WAITING_FOR_USB else UsbStatus.USB_DISCONNECTED,
                musicFolderLabel = saved.displayLabel(),
                hasSavedFolder = true,
                usbPresent = usbPresent,
                volumeIdentity = saved.volumeUuid
            )
            FolderResolveResult.FolderNotFound,
            FolderResolveResult.Ambiguous -> UsbUiState(
                status = UsbStatus.FOLDER_NOT_FOUND,
                musicFolderLabel = saved.displayLabel(),
                hasSavedFolder = true,
                usbPresent = true,
                volumeIdentity = saved.volumeUuid
            )
        }
    }

    private fun emit(state: UsbUiState) {
        lastState = state
        listener(state)
    }
}
