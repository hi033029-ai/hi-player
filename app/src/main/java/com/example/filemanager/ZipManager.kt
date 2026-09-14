package com.example.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ZipEntryInfo(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
)

/**
 * Extract and create zip archives using the standard java.util.zip APIs (no extra
 * dependency needed — this is built into the JDK/Android SDK).
 */
object ZipManager {

    private const val BUFFER_SIZE = 64 * 1024

    /** Lists entries without extracting — useful for previewing a zip's contents. */
    suspend fun listEntries(zipFile: File): Result<List<ZipEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(zipFile).use { zf ->
                zf.entries().asSequence().map { entry ->
                    ZipEntryInfo(
                        name = entry.name,
                        isDirectory = entry.isDirectory,
                        sizeBytes = entry.size,
                        compressedSizeBytes = entry.compressedSize,
                    )
                }.toList()
            }
        }
    }

    /**
     * Extracts every entry into destDir, preserving folder structure.
     *
     * SECURITY: validates that every entry's resolved output path stays inside
     * destDir before writing anything. A malicious/corrupt zip can contain entry
     * names like "../../../etc/something" ("zip slip") — without this check, naive
     * extraction code will happily write outside the intended folder. This check is
     * what makes that impossible here.
     */
    suspend fun extract(
        zipFile: File,
        destDir: File,
        onProgress: (OperationProgress) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!destDir.exists()) destDir.mkdirs()
            val canonicalDest = destDir.canonicalPath + File.separator

            val totalEntries = ZipFile(zipFile).use { it.size() }
            var extracted = 0

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                var index = 0
                while (entry != null) {
                    index++
                    val outFile = File(destDir, entry.name)
                    val canonicalOut = outFile.canonicalPath

                    if (!canonicalOut.startsWith(canonicalDest)) {
                        throw IOException("Blocked unsafe zip entry (path traversal): ${entry.name}")
                    }

                    onProgress(OperationProgress(index, totalEntries, entry.name))

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos, bufferSize = BUFFER_SIZE)
                        }
                        extracted++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            extracted
        }
    }

    /**
     * Creates a zip from the given files/folders. Folders are walked recursively and
     * their contents stored with paths relative to the folder itself (so zipping
     * "Movies/" produces entries like "Movies/file.mp4", not the full absolute path).
     */
    suspend fun create(
        sourceFiles: List<File>,
        outputZipFile: File,
        onProgress: (OperationProgress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val allFiles = mutableListOf<Pair<File, String>>() // file to entry-name
            sourceFiles.forEach { root ->
                collectEntries(root, root.parentFile ?: root, allFiles)
            }

            ZipOutputStream(FileOutputStream(outputZipFile)).use { zos ->
                allFiles.forEachIndexed { index, (file, entryName) ->
                    onProgress(OperationProgress(index + 1, allFiles.size, file.name))
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$entryName/"))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos, bufferSize = BUFFER_SIZE)
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    private fun collectEntries(file: File, base: File, out: MutableList<Pair<File, String>>) {
        val relativeName = file.relativeTo(base).path.replace(File.separatorChar, '/')
        out.add(file to relativeName)
        if (file.isDirectory) {
            file.listFiles()?.forEach { collectEntries(it, base, out) }
        }
    }
}
