package com.example.viewmodel

import com.example.model.VideoTrackInfo

/** Match only the saved file's supported track; do not apply an index to another file. */
internal fun matchingRememberedAudioTrack(tracks: List<VideoTrackInfo>, saved: VideoTrackInfo): VideoTrackInfo? {
    val matches = tracks.filter { it.isSupported && it.id == saved.id && it.label == saved.label &&
        it.language == saved.language && it.mimeType == saved.mimeType }
    return matches.singleOrNull() ?: matches.singleOrNull {
        it.trackGroupIndex == saved.trackGroupIndex && it.trackIndex == saved.trackIndex
    }
}
