package com.example.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class FileManagerEvent {
    data class Error(val message: String) : FileManagerEvent()
    data class Info(val message: String) : FileManagerEvent()
}

data class FileManagerUiState(
    val currentDir: File,
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val progress: OperationProgress? = null, // non-null while a zip/bulk op is running
    val clipboard: ClipboardOp? = null,       // pending copy/move, applied on "Paste"
)

data class ClipboardOp(val files: List<File>, val isCut: Boolean)

class PortableFileManagerViewModel(rootDir: File) : ViewModel() {

    private val _uiState = MutableStateFlow(FileManagerUiState(currentDir = rootDir))
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<FileManagerEvent?>(null)
    val events: StateFlow<FileManagerEvent?> = _events.asStateFlow()

    init { refresh() }

    // ── Navigation ──────────────────────────────────────────────────────────────
    fun navigateTo(dir: File) {
        _uiState.value = _uiState.value.copy(currentDir = dir, isSelectionMode = false, selectedPaths = emptySet())
        refresh()
    }

    /** Returns false if already at the root (caller can then pop the screen). */
    fun navigateUp(root: File): Boolean {
        val parent = _uiState.value.currentDir.parentFile
        if (parent == null || _uiState.value.currentDir == root) return false
        navigateTo(parent)
        return true
    }

    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        FileOperations.listFiles(_uiState.value.currentDir)
            .onSuccess { entries -> _uiState.value = _uiState.value.copy(entries = entries, isLoading = false) }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.value = FileManagerEvent.Error(e.message ?: "Could not read folder")
            }
    }

    // ── Selection ───────────────────────────────────────────────────────────────
    fun enterSelectionMode(initial: FileEntry) {
        _uiState.value = _uiState.value.copy(isSelectionMode = true, selectedPaths = setOf(initial.path))
    }

    fun toggleSelection(entry: FileEntry) {
        val current = _uiState.value.selectedPaths
        val updated = if (entry.path in current) current - entry.path else current + entry.path
        _uiState.value = _uiState.value.copy(
            selectedPaths = updated,
            isSelectionMode = updated.isNotEmpty(),
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedPaths = _uiState.value.entries.map { it.path }.toSet(),
            isSelectionMode = true,
        )
    }

    fun unselectAll() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet(), isSelectionMode = false)
    }

    private fun selectedEntries(): List<FileEntry> =
        _uiState.value.entries.filter { it.path in _uiState.value.selectedPaths }

    // ── Create / rename / delete ───────────────────────────────────────────────
    fun createFolder(name: String) = viewModelScope.launch {
        FileOperations.createFolder(_uiState.value.currentDir, name)
            .onSuccess { refresh() }
            .onFailure { _events.value = FileManagerEvent.Error(it.message ?: "Could not create folder") }
    }

    fun createFile(name: String) = viewModelScope.launch {
        FileOperations.createFile(_uiState.value.currentDir, name)
            .onSuccess { refresh() }
            .onFailure { _events.value = FileManagerEvent.Error(it.message ?: "Could not create file") }
    }

    fun rename(entry: FileEntry, newName: String) = viewModelScope.launch {
        FileOperations.rename(entry.file, newName)
            .onSuccess { refresh() }
            .onFailure { _events.value = FileManagerEvent.Error(it.message ?: "Rename failed") }
    }

    fun deleteSelected() = viewModelScope.launch {
        val targets = selectedEntries().map { it.file }
        if (targets.isEmpty()) return@launch
        FileOperations.deleteAll(targets) { progress ->
            _uiState.value = _uiState.value.copy(progress = progress)
        }
            .onSuccess {
                _uiState.value = _uiState.value.copy(progress = null, selectedPaths = emptySet(), isSelectionMode = false)
                refresh()
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(progress = null)
                _events.value = FileManagerEvent.Error(it.message ?: "Delete failed")
            }
    }

    // ── Copy / move ─────────────────────────────────────────────────────────────
    fun copySelectedToClipboard() {
        _uiState.value = _uiState.value.copy(clipboard = ClipboardOp(selectedEntries().map { it.file }, isCut = false))
    }

    fun cutSelectedToClipboard() {
        _uiState.value = _uiState.value.copy(clipboard = ClipboardOp(selectedEntries().map { it.file }, isCut = true))
    }

    fun pasteClipboard() = viewModelScope.launch {
        val clip = _uiState.value.clipboard ?: return@launch
        val destDir = _uiState.value.currentDir
        var failures = 0
        clip.files.forEachIndexed { index, file ->
            _uiState.value = _uiState.value.copy(progress = OperationProgress(index + 1, clip.files.size, file.name))
            val result = if (clip.isCut) FileOperations.move(file, destDir) else FileOperations.copy(file, destDir)
            result.onFailure { failures++ }
        }
        _uiState.value = _uiState.value.copy(progress = null, clipboard = null, selectedPaths = emptySet(), isSelectionMode = false)
        refresh()
        if (failures > 0) _events.value = FileManagerEvent.Error("$failures item(s) failed to paste")
    }

    // ── Properties ──────────────────────────────────────────────────────────────
    suspend fun getProperties(entry: FileEntry): FileProperties? =
        FileOperations.getProperties(entry.file).getOrNull()

    // ── Zip ─────────────────────────────────────────────────────────────────────
    fun extractZip(entry: FileEntry) = viewModelScope.launch {
        val destDir = File(entry.file.parentFile, entry.file.nameWithoutExtension)
        ZipManager.extract(entry.file, destDir) { progress ->
            _uiState.value = _uiState.value.copy(progress = progress)
        }
            .onSuccess { count ->
                _uiState.value = _uiState.value.copy(progress = null)
                _events.value = FileManagerEvent.Info("Extracted $count file(s) to ${destDir.name}")
                refresh()
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(progress = null)
                _events.value = FileManagerEvent.Error(it.message ?: "Extraction failed")
            }
    }

    fun compressSelectedToZip(zipName: String) = viewModelScope.launch {
        val targets = selectedEntries().map { it.file }
        if (targets.isEmpty()) return@launch
        val outputName = if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
        val outputFile = File(_uiState.value.currentDir, outputName)

        ZipManager.create(targets, outputFile) { progress ->
            _uiState.value = _uiState.value.copy(progress = progress)
        }
            .onSuccess {
                _uiState.value = _uiState.value.copy(progress = null, selectedPaths = emptySet(), isSelectionMode = false)
                refresh()
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(progress = null)
                _events.value = FileManagerEvent.Error(it.message ?: "Compression failed")
            }
    }

    fun consumeEvent() { _events.value = null }
}
