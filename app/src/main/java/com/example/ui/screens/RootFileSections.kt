package com.example.ui.screens

import com.example.model.FileItem

/** Root categories own disjoint paths; entering a folder still shows all its contents. */
data class RootFileSections(val archives: List<FileItem>, val documents: List<FileItem>, val folders: List<FileItem>) {
    fun items(scope: SelectAllScope): List<FileItem> = when (scope) {
        SelectAllScope.ARCHIVES -> archives
        SelectAllScope.DOCUMENTS -> documents
        SelectAllScope.FOLDERS -> folders
    }
    companion object {
        fun from(archives: List<FileItem>, documents: List<FileItem>, root: List<FileItem>): RootFileSections {
            val archiveItems = (archives + root.filter { !it.isDirectory && (it.isArchive || it.isApk) }).distinctBy { it.path }
            val archivePaths = archiveItems.map { it.path }.toHashSet()
            val documentItems = (documents + root.filter { !it.isDirectory && it.isDocument })
                .distinctBy { it.path }.filterNot { it.path in archivePaths }
            val categorizedPaths = archivePaths + documentItems.map { it.path }
            return RootFileSections(archiveItems, documentItems, root.filterNot { it.path in categorizedPaths }.distinctBy { it.path })
        }
    }
}
