package com.musicloop.car.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorClassifierTest {
    @Test
    fun decoderFailuresAreCodecLimitations() {
        assertTrue(
            PlaybackErrorClassifier.isCodecLimitation(
                errorCodeName = "ERROR_CODE_DECODER_INIT_FAILED",
                message = "Decoder init failed",
                causeName = "android.media.MediaCodec$CodecException",
                causeMessage = "Failed to initialize decoder"
            )
        )
        val formatted = PlaybackErrorClassifier.format(
            errorCodeName = "ERROR_CODE_DECODING_FAILED",
            message = "Decode failed",
            causeName = "java.lang.IllegalStateException",
            causeMessage = "codec"
        )
        assertTrue(formatted.contains("codec/container limitation"))
        assertTrue(formatted.contains("errorCode=ERROR_CODE_DECODING_FAILED"))
    }

    @Test
    fun ioFailuresAreNotCodecLimitations() {
        assertFalse(
            PlaybackErrorClassifier.isCodecLimitation(
                errorCodeName = "ERROR_CODE_IO_FILE_NOT_FOUND",
                message = "File not found",
                causeName = "java.io.FileNotFoundException",
                causeMessage = "missing"
            )
        )
    }
}
