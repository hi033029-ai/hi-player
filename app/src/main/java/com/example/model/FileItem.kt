package com.example.model

import java.io.File
import java.util.Locale

enum class FileType {
    DIRECTORY,
    VIDEO,
    AUDIO,
    IMAGE,
    ARCHIVE,
    DOCUMENT,
    OTHER
}

data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val childCount: Int = 0,
    val fileType: FileType = FileType.OTHER,
    val extension: String = file.extension.lowercase(Locale.ROOT)
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "$childCount items"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                else -> "$sizeBytes B"
            }
        }

    val isArchive: Boolean
        get() = fileType == FileType.ARCHIVE

    val isDocument: Boolean
        get() = fileType == FileType.DOCUMENT

    val isVideo: Boolean
        get() = fileType == FileType.VIDEO

    val isAudio: Boolean
        get() = fileType == FileType.AUDIO
}

data class ArchiveEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val isDirectory: Boolean,
    val time: Long = 0L
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "Folder"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                else -> "$sizeBytes B"
            }
        }
}
