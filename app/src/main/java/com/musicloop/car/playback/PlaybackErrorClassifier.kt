package com.musicloop.car.playback

object PlaybackErrorClassifier {
    fun isCodecLimitation(
        errorCodeName: String,
        message: String?,
        causeName: String?,
        causeMessage: String?
    ): Boolean {
        if (errorCodeName.contains("DECODER", ignoreCase = true) ||
            errorCodeName.contains("DECODING", ignoreCase = true) ||
            errorCodeName.contains("PARSING", ignoreCase = true)
        ) {
            return true
        }
        val blob = listOf(message, causeMessage, causeName)
            .filterNotNull()
            .joinToString(" ")
            .lowercase()
        return blob.contains("codec") ||
            blob.contains("decoder") ||
            blob.contains("mediacodec") ||
            blob.contains("unrecognized format")
    }

    fun format(
        errorCodeName: String,
        message: String?,
        causeName: String?,
        causeMessage: String?
    ): String {
        val codecHint = if (isCodecLimitation(errorCodeName, message, causeName, causeMessage)) {
            " codec/container limitation"
        } else {
            ""
        }
        return "errorCode=$errorCodeName cause=${causeName ?: "-"}:${causeMessage ?: "-"} message=${message ?: "-"}$codecHint"
    }
}
