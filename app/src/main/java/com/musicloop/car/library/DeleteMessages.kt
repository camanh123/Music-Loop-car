package com.musicloop.car.library

enum class DeleteFailureReason {
    OFFLINE,
    READ_ONLY,
    DUPLICATE_IN_PROGRESS,
    INVALID_IDENTITY,
    UNSUPPORTED_MEDIA,
    NOT_IN_LIBRARY,
    NOT_REGULAR_FILE,
    PATH_ESCAPE,
    MISSING,
    FILESYSTEM_REFUSED,
    PERMISSION_DENIED,
    MOUNT_CHANGED
}

data class FailedDelete(
    val identity: MediaIdentity,
    val reason: DeleteFailureReason
)

data class BatchDeleteResult(
    val succeeded: List<MediaIdentity> = emptyList(),
    val failed: List<FailedDelete> = emptyList(),
    val duplicateBlocked: Boolean = false
) {
    val requestedCount: Int get() = succeeded.size + failed.size
}

object DeleteMessages {
    fun singleBody(
        title: String,
        fileName: String,
        currentlyPlaying: Boolean,
        video: Boolean
    ): String {
        val nameLabel = if (video) "Tên:" else "Tên bài:"
        val playing = if (currentlyPlaying) {
            "\n\nTệp này đang được phát. Phát lại sẽ dừng trước khi xóa."
        } else {
            ""
        }
        return buildString {
            append(nameLabel)
            append('\n')
            append(title)
            append("\n\nTệp:\n")
            append(fileName)
            append("\n\nTệp sẽ bị xóa vĩnh viễn khỏi USB.\nThao tác này không thể hoàn tác.")
            append(playing)
        }
    }

    fun batchBody(audioCount: Int, videoCount: Int, totalBytes: Long): String {
        val total = audioCount + videoCount
        val lines = mutableListOf<String>()
        if (audioCount > 0 && videoCount == 0) {
            lines += "$audioCount bài hát"
        } else if (videoCount > 0 && audioCount == 0) {
            lines += "$videoCount video"
        } else {
            if (audioCount > 0) {
                lines += "$audioCount bài hát"
            }
            if (videoCount > 0) {
                lines += "$videoCount video"
            }
        }
        return buildString {
            lines.forEach { line ->
                append(line)
                append('\n')
            }
            append("\nDung lượng: ")
            append(formatSize(totalBytes))
            append("\n\nCác tệp sẽ bị xóa vĩnh viễn khỏi USB.\nKhông thể hoàn tác.")
            if (total <= 0) {
                // Keep structure even if empty; caller should not show this.
            }
        }
    }

    fun progress(current: Int, total: Int): String = "Đang xóa $current / $total"

    fun partialSummary(deleted: Int, total: Int): String = "ĐÃ XÓA $deleted/$total TỆP"

    fun formatSize(bytes: Long): String {
        if (bytes < 1024L) {
            return "$bytes B"
        }
        val kb = bytes / 1024L
        if (kb < 1024L) {
            return "$kb KB"
        }
        val mb = kb / 1024L
        if (mb < 1024L) {
            return "$mb MB"
        }
        val gb = mb / 1024L
        return "$gb GB"
    }

    fun userReason(reason: DeleteFailureReason): String {
        return when (reason) {
            DeleteFailureReason.OFFLINE -> "USB đã bị ngắt kết nối"
            DeleteFailureReason.READ_ONLY -> "USB hiện chỉ cho phép đọc"
            DeleteFailureReason.DUPLICATE_IN_PROGRESS -> "Đang xóa tệp"
            DeleteFailureReason.INVALID_IDENTITY -> "Tệp không hợp lệ"
            DeleteFailureReason.UNSUPPORTED_MEDIA -> "Loại tệp không được hỗ trợ"
            DeleteFailureReason.NOT_IN_LIBRARY -> "Tệp không có trong thư viện"
            DeleteFailureReason.NOT_REGULAR_FILE -> "Không phải tệp media"
            DeleteFailureReason.PATH_ESCAPE -> "Đường dẫn không hợp lệ"
            DeleteFailureReason.MISSING -> "Tệp không còn trên USB"
            DeleteFailureReason.FILESYSTEM_REFUSED -> "Hệ thống từ chối xóa tệp"
            DeleteFailureReason.PERMISSION_DENIED -> "USB hiện chỉ cho phép đọc"
            DeleteFailureReason.MOUNT_CHANGED -> "USB đã thay đổi"
        }
    }
}
