package com.example.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Core CRUD operations: read (list), write (create), rename, delete, copy, move,
 * properties. All suspend + IO-dispatched since these touch disk and can be slow
 * on large folders/files.
 */
object FileOperations {

    suspend fun listFiles(dir: File): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val files = dir.listFiles() ?: throw IOException("Cannot read directory: ${dir.path}")
            files
                .map { FileEntry(it) }
                .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(parent, name.sanitizedFileName())
            if (target.exists()) throw IOException("\"$name\" already exists")
            if (!target.mkdirs()) throw IOException("Could not create folder \"$name\"")
            target
        }
    }

    suspend fun createFile(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(parent, name.sanitizedFileName())
            if (target.exists()) throw IOException("\"$name\" already exists")
            if (!target.createNewFile()) throw IOException("Could not create file \"$name\"")
            target
        }
    }

    suspend fun readTextFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching { file.readText() }
    }

    suspend fun writeTextFile(file: File, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { file.writeText(content) }
    }

    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(file.parentFile, newName.sanitizedFileName())
            if (target.exists()) throw IOException("\"$newName\" already exists")
            if (!file.renameTo(target)) throw IOException("Rename failed for \"${file.name}\"")
            target
        }
    }

    /** Recursive delete for folders. Returns the count of items actually removed. */
    suspend fun delete(file: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { deleteRecursively(file) }
    }

    suspend fun deleteAll(
        files: List<File>,
        onProgress: (OperationProgress) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var total = 0
            files.forEachIndexed { index, f ->
                onProgress(OperationProgress(index + 1, files.size, f.name))
                total += deleteRecursively(f)
            }
            total
        }
    }

    private fun deleteRecursively(file: File): Int {
        var count = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach { count += deleteRecursively(it) }
        }
        if (file.delete()) count++
        return count
    }

    suspend fun copy(src: File, destDir: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = uniqueTarget(destDir, src.name)
            copyRecursively(src, target)
            target
        }
    }

    suspend fun move(src: File, destDir: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = uniqueTarget(destDir, src.name)
            if (!src.renameTo(target)) {
                // renameTo can fail across filesystems/volumes — fall back to copy+delete.
                copyRecursively(src, target)
                deleteRecursively(src)
            }
            target
        }
    }

    private fun copyRecursively(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursively(child, File(dest, child.name))
            }
        } else {
            src.copyTo(dest, overwrite = false)
        }
    }

    /** Appends " (1)", " (2)"... if a same-named item already exists at the destination. */
    private fun uniqueTarget(destDir: File, name: String): File {
        var candidate = File(destDir, name)
        if (!candidate.exists()) return candidate
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(destDir, "$base ($n)$ext")
            n++
        }
        return candidate
    }

    suspend fun getProperties(file: File): Result<FileProperties> = withContext(Dispatchers.IO) {
        runCatching {
            FileProperties(
                name = file.name,
                path = file.absolutePath,
                sizeBytes = if (file.isDirectory) folderSize(file) else file.length(),
                itemCount = if (file.isDirectory) countItems(file) else null,
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                readable = file.canRead(),
                writable = file.canWrite(),
            )
        }
    }

    private fun folderSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += if (f.isDirectory) folderSize(f) else f.length()
        }
        return size
    }

    private fun countItems(dir: File): Int {
        var count = 0
        dir.listFiles()?.forEach { f ->
            count++
            if (f.isDirectory) count += countItems(f)
        }
        return count
    }
}

/** Strips path separators and other characters that would break a plain file name. */
private fun String.sanitizedFileName(): String =
    trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "Untitled" }

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    do {
        value /= 1024
        unitIndex++
    } while (value >= 1024 && unitIndex < units.lastIndex)
    return "%.1f %s".format(value, units[unitIndex])
}
