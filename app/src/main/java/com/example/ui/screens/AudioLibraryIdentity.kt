package com.example.ui.screens

import com.example.model.AudioItem

/** Imported/SAF media can share ID zero. Stable identity prevents wrong row/artwork reuse. */
internal fun audioLibraryKey(track: AudioItem): String = track.path.ifBlank {
    track.uri.toString().takeUnless { it.isBlank() } ?: "audio:${track.id}:${track.title}:${track.artist}"
}
