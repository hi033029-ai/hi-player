package com.example.ui.screens

/** Match the same groups that are displayed by the video library, never path prefixes of siblings. */
internal fun videoBelongsToSelectedFolder(key: String, path: String, folderName: String): Boolean = when {
    key.startsWith("name:") || key.startsWith("dir:") -> key == com.example.model.videoFolderKey(path, folderName)
    key.startsWith("path:") -> {
        val directory = key.removePrefix("path:").trimEnd('/')
        directory.isNotEmpty() && path.isNotBlank() && path.startsWith("$directory/")
    }
    else -> false
}
