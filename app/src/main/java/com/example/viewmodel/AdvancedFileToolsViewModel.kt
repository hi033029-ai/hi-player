package com.example.viewmodel

import android.app.Application
import android.media.MediaScannerConnection
import android.system.Os
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

internal data class FileFingerprint(val file: File, val size: Long, val modified: Long)
internal data class RenamePreview(val source: FileFingerprint, val target: File)
internal data class AdvancedFileState(
    val busy: Boolean = false, val message: String = "", val preview: List<RenamePreview> = emptyList(),
    val duplicates: List<List<File>> = emptyList(), val revision: Int = 0,
)

/** Bounded, explicit folder/selection operations. Never recurses or automatically deletes a duplicate. */
internal class AdvancedFileToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AdvancedFileState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private fun fingerprint(file: File): FileFingerprint? = runCatching {
        if (!file.isFile || file.canonicalPath != file.absolutePath || file.name.startsWith('.')) return null
        FileFingerprint(file, file.length(), file.lastModified())
    }.getOrNull()
    private fun unchanged(item: FileFingerprint) = fingerprint(item.file) == item

    fun cancel() { job?.cancel() }
    fun resetIdle() { if (!_state.value.busy) _state.value = _state.value.copy(preview = emptyList(), duplicates = emptyList()) }
    fun clearPreview() { if (!_state.value.busy) _state.value = _state.value.copy(preview = emptyList()) }

    fun previewRename(files: List<File>, prefix: String, start: Int) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, preview = emptyList(), duplicates = emptyList(), message = "Checking selected files…")
        job = viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.IO) {
                    require(files.size in 1..200 && files.distinct().size == files.size)
                    val label = prefix.trim()
                    require(label.isNotBlank() && label.length <= 40 && label.none { it == '/' || it == '\\' || it.isISOControl() } && start in 1..999999)
                    val parent = files.first().parentFile?.canonicalFile ?: error("No folder")
                    files.sortedBy { it.name }.mapIndexed { index, file ->
                        currentCoroutineContext().ensureActive()
                        require(file.parentFile?.canonicalFile == parent)
                        require(!com.example.model.PendingDownloadFormat.isPending(file.name))
                        val source = requireNotNull(fingerprint(file))
                        val suffix = file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
                        val target = File(parent, "${label}_${(start + index).toString().padStart(3, '0')}$suffix")
                        require(!target.exists() && target.name.toByteArray(Charsets.UTF_8).size <= 255)
                        RenamePreview(source, target)
                    }
                }
                _state.value = _state.value.copy(preview = plan, message = "Review ${plan.size} names. Nothing has been renamed yet.")
            } catch (e: CancellationException) { _state.value = _state.value.copy(message = "Preview cancelled. No files changed."); throw e
            } catch (_: Exception) { _state.value = _state.value.copy(message = "Couldn't prepare names. Select 1–200 regular files in one folder, use a valid prefix, and avoid existing target names.")
            } finally { _state.value = _state.value.copy(busy = false) }
        }
    }

    fun rename() {
        if (_state.value.busy || _state.value.preview.isEmpty()) return
        val plan = _state.value.preview
        _state.value = _state.value.copy(busy = true, preview = emptyList(), message = "Renaming… copying safely before removing each original")
        job = viewModelScope.launch {
            var completed = 0; var failed = 0; var cancelled = false
            val changed = mutableListOf<String>()
            try {
                withContext(Dispatchers.IO) {
                    // No operation begins if the preview has become stale.
                    require(plan.all { unchanged(it.source) && !it.target.exists() })
                    for (item in plan) {
                        currentCoroutineContext().ensureActive()
                        changed += item.source.file.absolutePath; changed += item.target.absolutePath
                        val success = try {
                            require(unchanged(item.source))
                            moveWithoutOverwrite(item.source, item.target)
                            true
                        } catch (e: CancellationException) { throw e
                        } catch (_: Exception) { false }
                        if (success) completed++ else failed++
                        withContext(Dispatchers.Main) { _state.value = _state.value.copy(message = "$completed renamed · $failed failed · ${plan.size} selected") }
                    }
                }
            } catch (e: CancellationException) { cancelled = true
            } catch (_: Exception) { failed = plan.size - completed
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (changed.isNotEmpty()) runCatching { MediaScannerConnection.scanFile(getApplication(), changed.toTypedArray(), null, null) }
                }
                _state.value = _state.value.copy(busy = false, revision = _state.value.revision + 1,
                    message = "$completed renamed · $failed failed" + if (cancelled) ". Stopped; completed renames were kept. A complete copy may remain if stopped before removing its original; inspect the folder." else if (failed > 0) ". Check storage access, available space or changed names. Original data is not overwritten; inspect the folder before retrying." else ". Finished.")
            }
        }
    }

    /** Exclusive destination creation also works on emulated/FAT volumes without
     * hard links. Originals are removed only after a complete synced copy and a
     * fresh source identity check. Cancellation retains completed files only. */
    private suspend fun moveWithoutOverwrite(source: FileFingerprint, target: File) {
        val input = java.io.FileInputStream(source.file)
        input.use {
            val identity = Os.fstat(input.fd)
            val fd = Os.open(target.absolutePath, android.system.OsConstants.O_WRONLY or
                android.system.OsConstants.O_CREAT or android.system.OsConstants.O_EXCL, 384)
            val created = Os.fstat(fd)
            var complete = false
            try {
                java.io.FileOutputStream(fd).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= source.size)
                        output.write(buffer, 0, count)
                    }
                    require(copied == source.size && unchanged(source))
                    output.fd.sync()
                    complete = true
                }
                currentCoroutineContext().ensureActive()
                val now = Os.stat(source.file.absolutePath)
                require(now.st_ino == identity.st_ino && now.st_dev == identity.st_dev && unchanged(source))
                target.setLastModified(source.modified)
                Os.remove(source.file.absolutePath)
            } finally {
                // Delete only our own incomplete destination, never an outside
                // replacement. Complete copies stay if source removal failed.
                if (!complete && source.file.exists()) runCatching {
                    val now = Os.stat(target.absolutePath)
                    if (now.st_ino == created.st_ino && now.st_dev == created.st_dev) Os.remove(target.absolutePath)
                }
            }
        }
    }

    fun scanDuplicates(directory: File) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, preview = emptyList(), duplicates = emptyList(), message = "Reading this folder…")
        job = viewModelScope.launch {
            try {
                var skipped = 0
                var checked = 0
                val groups = withContext(Dispatchers.IO) {
                    val all = directory.listFiles() ?: error("Folder unavailable")
                    val files = all.asSequence().filter { it.isFile && !com.example.model.PendingDownloadFormat.isPending(it.name) }.take(2001).toList()
                    require(files.size <= 2000) // refuse, rather than presenting an arbitrary subset as complete
                    val candidates = files.mapNotNull { file -> fingerprint(file).also { if (it == null) skipped++ } }.groupBy { it.size }.values.filter { it.size > 1 }.flatten()
                    val hashes = mutableMapOf<String, MutableList<File>>()
                    var readBytes = 0L
                    val limit = 20L * 1024 * 1024 * 1024
                    val buffer = ByteArray(128 * 1024)
                    for (item in candidates) {
                        currentCoroutineContext().ensureActive()
                        if (item.size > limit - readBytes) { skipped++; continue }
                        try {
                            require(unchanged(item))
                            val digest = MessageDigest.getInstance("SHA-256")
                            var consumed = 0L
                            item.file.inputStream().buffered().use { input ->
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val remaining = minOf(item.size - consumed, limit - readBytes)
                                    if (remaining <= 0L) break
                                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (count < 0) break
                                    readBytes += count; consumed += count
                                    digest.update(buffer, 0, count)
                                }
                            }
                            require(unchanged(item) && consumed == item.size)
                            val hash = digest.digest().joinToString("") { "%02x".format(it) }
                            hashes.getOrPut("${item.size}:$hash") { mutableListOf() }.add(item.file)
                            checked++
                        } catch (e: CancellationException) { throw e
                        } catch (_: Exception) { skipped++ }
                        withContext(Dispatchers.Main) { _state.value = _state.value.copy(message = "$checked candidate files checked · $skipped skipped") }
                    }
                    hashes.values.filter { it.size > 1 }.map { it.toList() }
                }
                _state.value = _state.value.copy(duplicates = groups, message = "${groups.size} matching-content groups · $checked candidate files checked · $skipped skipped. ${if (skipped > 0) "Scan incomplete." else "Folder scan finished."} No files were deleted.")
            } catch (e: CancellationException) { _state.value = _state.value.copy(message = "Scan cancelled. No files changed."); throw e
            } catch (_: Exception) { _state.value = _state.value.copy(message = "Couldn't scan this folder. Check storage access or choose a folder with at most 2,000 files.")
            } finally { _state.value = _state.value.copy(busy = false) }
        }
    }
}
