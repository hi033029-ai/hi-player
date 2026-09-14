package com.example.filemanager

import java.io.File

/** Lightweight, display-ready wrapper around java.io.File. */
data class FileEntry(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val sizeBytes: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val extension: String = file.extension.lowercase(),
) {
    val isZip: Boolean get() = extension == "zip"

    val kind: FileKind
        get() = when {
            isDirectory -> FileKind.FOLDER
            isZip -> FileKind.ARCHIVE
            extension in VIDEO_EXTENSIONS -> FileKind.VIDEO
            extension in AUDIO_EXTENSIONS -> FileKind.AUDIO
            extension in IMAGE_EXTENSIONS -> FileKind.IMAGE
            extension in DOC_EXTENSIONS -> FileKind.DOCUMENT
            else -> FileKind.OTHER
        }

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp")
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        private val DOC_EXTENSIONS = setOf("pdf", "txt", "doc", "docx", "xls", "xlsx")
    }
}

enum class FileKind { FOLDER, ARCHIVE, VIDEO, AUDIO, IMAGE, DOCUMENT, OTHER }

data class FileProperties(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val itemCount: Int?,   // null for a plain file, populated for folders
    val lastModified: Long,
    val isDirectory: Boolean,
    val readable: Boolean,
    val writable: Boolean,
)

/** Progress callback shape shared by zip extract/create and bulk copy/move/delete. */
data class OperationProgress(
    val currentIndex: Int,
    val total: Int,
    val currentItemName: String,
)
