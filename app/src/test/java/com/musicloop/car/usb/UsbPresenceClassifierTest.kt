package com.musicloop.car.usb

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbPresenceClassifierTest {
    @Test
    fun emptySnapshotWithoutKnownVolumeIsNotDetected() {
        assertEquals(
            UsbHostState.USB_NOT_DETECTED,
            UsbPresenceClassifier.classify(
                removableMounted = 0,
                scanning = false,
                scanFailed = false,
                scanCompleted = false,
                hadKnownVolume = false
            )
        )
    }

    @Test
    fun emptySnapshotAfterKnownVolumeIsOffline() {
        assertEquals(
            UsbHostState.USB_OFFLINE,
            UsbPresenceClassifier.classify(
                removableMounted = 0,
                scanning = false,
                scanFailed = false,
                scanCompleted = false,
                hadKnownVolume = true
            )
        )
    }

    @Test
    fun mountedVolumeStates() {
        assertEquals(
            UsbHostState.USB_SCANNING,
            UsbPresenceClassifier.classify(1, scanning = true, scanFailed = false, scanCompleted = false, hadKnownVolume = true)
        )
        assertEquals(
            UsbHostState.USB_READY,
            UsbPresenceClassifier.classify(1, scanning = false, scanFailed = false, scanCompleted = true, hadKnownVolume = true)
        )
        assertEquals(
            UsbHostState.USB_ONLINE,
            UsbPresenceClassifier.classify(1, scanning = false, scanFailed = false, scanCompleted = false, hadKnownVolume = true)
        )
        assertEquals(
            UsbHostState.USB_ERROR,
            UsbPresenceClassifier.classify(1, scanning = false, scanFailed = true, scanCompleted = false, hadKnownVolume = true)
        )
    }

    @Test
    fun missingVolumeIsNeverOnlineEvenIfScanningFlagIsSet() {
        assertEquals(
            UsbHostState.USB_NOT_DETECTED,
            UsbPresenceClassifier.classify(
                removableMounted = 0,
                scanning = true,
                scanFailed = true,
                scanCompleted = true,
                hadKnownVolume = false
            )
        )
    }
}
