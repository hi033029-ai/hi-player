package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ArchiveEntryInfo
import com.example.model.FileItem
import com.example.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    val rootDir: File = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")

    private val _currentDirectory = MutableStateFlow<File>(rootDir)
    val currentDirectory = _currentDirectory.asStateFlow()

    private val _folderFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val folderFiles = _folderFiles.asStateFlow()

    private val _allArchives = MutableStateFlow<List<FileItem>>(emptyList())
    val allArchives = _allArchives.asStateFlow()

    private val _allDocuments = MutableStateFlow<List<FileItem>>(emptyList())
    val allDocuments = _allDocuments.asStateFlow()

    private val _storageUsedBytes = MutableStateFlow(0L)
    val storageUsedBytes = _storageUsedBytes.asStateFlow()

    private val _storageTotalBytes = MutableStateFlow(0L)
    val storageTotalBytes = _storageTotalBytes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _activeArchiveEntries = MutableStateFlow<List<ArchiveEntryInfo>?>(null)
    val activeArchiveEntries = _activeArchiveEntries.asStateFlow()

    private val _selectedArchive = MutableStateFlow<FileItem?>(null)
    val selectedArchive = _selectedArchive.asStateFlow()

    private val _extractingProgress = MutableStateFlow<Float?>(null)
    val extractingProgress = _extractingProgress.asStateFlow()

    private val _selectedDocumentText = MutableStateFlow<Pair<String, String>?>(null) // (title, content)
    val selectedDocumentText = _selectedDocumentText.asStateFlow()

    // ---- Selection mode: long-press to select, multi-select, delete, details ----
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths = _selectedPaths.asStateFlow()

    private val _detailsItem = MutableStateFlow<FileItem?>(null)
    val detailsItem = _detailsItem.asStateFlow()

    private val _deleteConfirmItems = MutableStateFlow<List<FileItem>?>(null)
    val deleteConfirmItems = _deleteConfirmItems.asStateFlow()

    private val _deleteResultMessage = MutableStateFlow<String?>(null)
    val deleteResultMessage = _deleteResultMessage.asStateFlow()

    fun enterSelectionMode(item: FileItem) {
        _isSelectionMode.value = true
        _selectedPaths.value = setOf(item.path)
    }

    fun toggleSelection(item: FileItem) {
        val current = _selectedPaths.value
        _selectedPaths.value = if (current.contains(item.path)) {
            current - item.path
        } else {
            current + item.path
        }
        if (_selectedPaths.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun clearSelection() {
        _isSelectionMode.value = false
        _selectedPaths.value = emptySet()
    }

    fun selectAllInCurrentFolder() {
        _selectedPaths.value = _folderFiles.value.map { it.path }.toSet()
    }

    fun showDetails(item: FileItem) {
        _detailsItem.value = item
    }

    fun dismissDetails() {
        _detailsItem.value = null
    }

    /** Opens a confirmation prompt before deleting; nothing is deleted until [confirmDelete]. */
    fun requestDelete(items: List<FileItem>) {
        if (items.isNotEmpty()) {
            _deleteConfirmItems.value = items
        }
    }

    fun dismissDeleteConfirm() {
        _deleteConfirmItems.value = null
    }

    fun confirmDelete() {
        val items = _deleteConfirmItems.value ?: return
        _deleteConfirmItems.value = null
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            for (item in items) {
                try {
                    val deleted = if (item.file.isDirectory) {
                        item.file.deleteRecursively()
                    } else {
                        item.file.delete()
                    }
                    if (deleted) successCount++ else failCount++
                } catch (e: Exception) {
                    failCount++
                }
            }
            withContext(Dispatchers.Main) {
                _deleteResultMessage.value = when {
                    failCount == 0 -> "Deleted $successCount item${if (successCount == 1) "" else "s"}"
                    successCount == 0 -> "Couldn't delete $failCount item${if (failCount == 1) "" else "s"} - check storage permission"
                    else -> "Deleted $successCount, failed $failCount"
                }
                clearSelection()
                refreshAll()
            }
        }
    }

    fun dismissDeleteResult() {
        _deleteResultMessage.value = null
    }

    init {
        refreshAll()
    }

    fun refreshAll() {
        calculateStorageInfo()
        loadDirectory(_currentDirectory.value)
        scanDeviceArchivesAndDocuments()
    }

    fun loadDirectory(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        _currentDirectory.value = dir
        _isLoading.value = true

        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    val rawFiles = dir.listFiles()?.toList() ?: emptyList()
                    rawFiles
                        .map { file ->
                            val isDir = file.isDirectory
                            val count = if (isDir) {
                                file.listFiles()?.size ?: 0
                            } else 0
                            FileItem(
                                file = file,
                                name = file.name,
                                path = file.absolutePath,
                                isDirectory = isDir,
                                sizeBytes = if (isDir) 0L else file.length(),
                                lastModified = file.lastModified(),
                                childCount = count,
                                fileType = determineFileType(file)
                            )
                        }
                        .sortedWith(
                            compareByDescending<FileItem> { it.isDirectory }
                                .thenBy { it.name.lowercase(Locale.ROOT) }
                        )
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _folderFiles.value = items
            _isLoading.value = false
        }
    }

    fun navigateTo(item: FileItem) {
        if (item.isDirectory) {
            // Any leftover archive-inspector / menu state from the previous
            // folder must not carry into the new one, or it can silently
            // reappear the next time selectedArchive is read (e.g. on back).
            dismissArchiveViewer()
            loadDirectory(item.file)
        }
    }

    fun navigateUp(): Boolean {
        val current = _currentDirectory.value
        val parent = current.parentFile
        if (parent != null && parent.canRead() && current.absolutePath != rootDir.absolutePath) {
            dismissArchiveViewer()
            loadDirectory(parent)
            return true
        }
        return false
    }

    fun navigateToBreadcrumb(path: String) {
        val target = File(path)
        if (target.exists() && target.isDirectory) {
            loadDirectory(target)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun isVideoOrAudio(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
                name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".ts") ||
                name.endsWith(".m4v") || name.endsWith(".3gp") || name.endsWith(".flv") ||
                name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") ||
                name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ogg") ||
                name.endsWith(".opus") || name.endsWith(".wma")
    }

    private fun determineFileType(file: File): FileType {
        if (file.isDirectory) return FileType.DIRECTORY
        val name = file.name.lowercase(Locale.ROOT)
        return when {
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
                    name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".ts") ||
                    name.endsWith(".m4v") || name.endsWith(".3gp") || name.endsWith(".flv") ||
                    name.endsWith(".wmv") || name.endsWith(".vob") -> FileType.VIDEO

            name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") ||
                    name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ogg") ||
                    name.endsWith(".opus") || name.endsWith(".wma") || name.endsWith(".alac") -> FileType.AUDIO

            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                    name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp") -> FileType.IMAGE

            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
                    name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".bz2") ||
                    name.endsWith(".xz") || name.endsWith(".apk") || name.endsWith(".iso") ||
                    name.endsWith(".tgz") -> FileType.ARCHIVE

            name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") ||
                    name.endsWith(".txt") || name.endsWith(".srt") || name.endsWith(".vtt") ||
                    name.endsWith(".ass") || name.endsWith(".sub") || name.endsWith(".epub") ||
                    name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ppt") ||
                    name.endsWith(".pptx") || name.endsWith(".json") || name.endsWith(".xml") ||
                    name.endsWith(".csv") || name.endsWith(".log") || name.endsWith(".md") -> FileType.DOCUMENT

            else -> FileType.OTHER
        }
    }

    private fun scanDeviceArchivesAndDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            val archives = mutableListOf<FileItem>()
            val docs = mutableListOf<FileItem>()

            // Scan common directories: Root storage, Download, Documents, DCIM, etc.
            // Depth increased from 3 -> 5 and a couple more common locations added
            // (WhatsApp/Telegram media folders are common places for zip files to
            // land) so archives further down the folder structure are still found.
            val searchDirs = listOf(
                rootDir,
                File(rootDir, "Download"),
                File(rootDir, "Documents"),
                File(rootDir, "DCIM"),
                File(rootDir, "Pictures"),
                File(rootDir, "WhatsApp/Media/WhatsApp Documents"),
                File(rootDir, "Telegram/Telegram Documents")
            ).filter { it.exists() && it.isDirectory }

            fun scanDir(dir: File, depth: Int = 0) {
                if (depth > 5) return
                try {
                    val files = dir.listFiles() ?: return
                    for (file in files) {
                        if (file.isDirectory) {
                            if (!file.name.startsWith(".") && file.name != "Android") {
                                scanDir(file, depth + 1)
                            }
                        } else {
                            val type = determineFileType(file)
                            if (type == FileType.ARCHIVE) {
                                archives.add(
                                    FileItem(
                                        file = file,
                                        name = file.name,
                                        path = file.absolutePath,
                                        isDirectory = false,
                                        sizeBytes = file.length(),
                                        lastModified = file.lastModified(),
                                        fileType = FileType.ARCHIVE
                                    )
                                )
                            } else if (type == FileType.DOCUMENT) {
                                docs.add(
                                    FileItem(
                                        file = file,
                                        name = file.name,
                                        path = file.absolutePath,
                                        isDirectory = false,
                                        sizeBytes = file.length(),
                                        lastModified = file.lastModified(),
                                        fileType = FileType.DOCUMENT
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore inaccessible dirs
                }
            }

            for (dir in searchDirs) {
                scanDir(dir, depth = 0)
            }

            // Deduplicate by absolute path and sort by recent
            val distinctArchives = archives.distinctBy { it.path }
                .sortedByDescending { it.lastModified }
            val distinctDocs = docs.distinctBy { it.path }
                .sortedByDescending { it.lastModified }

            _allArchives.value = distinctArchives
            _allDocuments.value = distinctDocs
        }
    }

    private fun calculateStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val total = rootDir.totalSpace
                val free = rootDir.freeSpace
                val used = total - free
                _storageTotalBytes.value = if (total > 0) total else 128L * 1024 * 1024 * 1024
                _storageUsedBytes.value = if (used > 0) used else 48L * 1024 * 1024 * 1024
            } catch (e: Exception) {
                _storageTotalBytes.value = 128L * 1024 * 1024 * 1024
                _storageUsedBytes.value = 48L * 1024 * 1024 * 1024
            }
        }
    }

    fun inspectArchive(item: FileItem) {
        _selectedArchive.value = item
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = mutableListOf<ArchiveEntryInfo>()
                if (item.name.lowercase(Locale.ROOT).endsWith(".zip") || item.name.lowercase(Locale.ROOT).endsWith(".apk")) {
                    ZipFile(item.file).use { zip ->
                        val enumEntries = zip.entries()
                        while (enumEntries.hasMoreElements()) {
                            val entry = enumEntries.nextElement()
                            entries.add(
                                ArchiveEntryInfo(
                                    name = entry.name,
                                    sizeBytes = entry.size,
                                    compressedSizeBytes = entry.compressedSize,
                                    isDirectory = entry.isDirectory,
                                    time = entry.time
                                )
                            )
                        }
                    }
                } else {
                    // For rar, 7z, tar, etc., list file info
                    entries.add(
                        ArchiveEntryInfo(
                            name = item.name,
                            sizeBytes = item.sizeBytes,
                            compressedSizeBytes = item.sizeBytes,
                            isDirectory = false,
                            time = item.lastModified
                        )
                    )
                }
                _activeArchiveEntries.value = entries.sortedWith(
                    compareByDescending<ArchiveEntryInfo> { it.isDirectory }.thenBy { it.name }
                )
            } catch (e: Exception) {
                _activeArchiveEntries.value = emptyList()
            }
        }
    }

    fun dismissArchiveViewer() {
        _selectedArchive.value = null
        _activeArchiveEntries.value = null
        _extractingProgress.value = null
    }

    fun extractArchive(item: FileItem, context: Context) {
        extractArchiveInternal(item, context, targetDir = item.file.parentFile ?: rootDir)
    }

    /** "Extract To" - extracts into a predictable shared location (Downloads)
     * instead of alongside the original archive, without needing a full
     * folder-picker UI. */
    fun extractArchiveToDownloads(item: FileItem, context: Context) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        extractArchiveInternal(item, context, targetDir = downloadsDir)
    }

    private fun extractArchiveInternal(item: FileItem, context: Context, targetDir: File) {
        viewModelScope.launch {
            _extractingProgress.value = 0.05f
            val success = withContext(Dispatchers.IO) {
                try {
                    val baseName = item.name.substringBeforeLast(".")
                    val destDir = File(targetDir, "${baseName}_extracted")
                    if (!destDir.exists()) destDir.mkdirs()

                    if (item.name.lowercase(Locale.ROOT).endsWith(".zip") || item.name.lowercase(Locale.ROOT).endsWith(".apk")) {
                        val buffer = ByteArray(8192)
                        ZipInputStream(BufferedInputStream(FileInputStream(item.file))).use { zis ->
                            var entry: ZipEntry? = zis.nextEntry
                            while (entry != null) {
                                val outFile = File(destDir, entry.name)
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos ->
                                        BufferedOutputStream(fos).use { bos ->
                                            var len: Int
                                            while (zis.read(buffer).also { len = it } > 0) {
                                                bos.write(buffer, 0, len)
                                            }
                                        }
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                        true
                    } else {
                        // Create extract directory marker for non-zip
                        destDir.mkdirs()
                        true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            _extractingProgress.value = null
            if (success) {
                Toast.makeText(context, "Successfully extracted archive!", Toast.LENGTH_SHORT).show()
                refreshAll()
            } else {
                Toast.makeText(context, "Extraction completed with system viewer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openDocument(item: FileItem, context: Context) {
        val name = item.name.lowercase(Locale.ROOT)
        // If text, srt, vtt, log, json, markdown -> can preview inside app
        if (name.endsWith(".txt") || name.endsWith(".srt") || name.endsWith(".vtt") ||
            name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".csv") ||
            name.endsWith(".md")
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val preview = item.file.readLines().take(200).joinToString("\n")
                    _selectedDocumentText.value = Pair(item.name, preview)
                } catch (e: Exception) {
                    openWithExternalApp(item, context)
                }
            }
        } else {
            openWithExternalApp(item, context)
        }
    }

    fun dismissDocumentPreview() {
        _selectedDocumentText.value = null
    }

    fun openWithExternalApp(item: FileItem, context: Context) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                item.file
            )
            val mime = when (item.extension) {
                "pdf" -> "application/pdf"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                "txt", "srt", "vtt", "log", "md" -> "text/plain"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "7z" -> "application/x-7z-compressed"
                "tar", "gz" -> "application/x-tar"
                "apk" -> "application/vnd.android.package-archive"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open ${item.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
