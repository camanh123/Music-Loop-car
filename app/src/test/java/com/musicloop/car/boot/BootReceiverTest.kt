package com.musicloop.car.boot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {

    @Test
    fun recognizesBootAndQuickBootActions() {
        assertTrue(BootReceiver.isBootAction("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootReceiver.isBootAction(BootReceiver.ACTION_QUICKBOOT))
        assertTrue(BootReceiver.isBootAction(BootReceiver.ACTION_HTC_QUICKBOOT))
        assertFalse(BootReceiver.isBootAction("android.intent.action.MEDIA_MOUNTED"))
    }
}
