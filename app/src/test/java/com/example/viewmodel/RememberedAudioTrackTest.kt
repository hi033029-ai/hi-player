package com.example.viewmodel

import androidx.media3.common.C
import com.example.model.VideoTrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RememberedAudioTrackTest {
    private fun track(id: String = "1", language: String? = "hi", group: Int = 0,
                      index: Int = 0, supported: Boolean = true) = VideoTrackInfo(
        id = id, label = "Hindi", language = language, mimeType = "audio/aac",
        isSelected = false, isSupported = supported, trackType = C.TRACK_TYPE_AUDIO,
        trackGroupIndex = group, trackIndex = index,
    )

    @Test fun restoresExactTrackIncludingUnknownLanguage() {
        val saved = track(language = null, group = 2)
        assertEquals(saved, matchingRememberedAudioTrack(listOf(track(), saved), saved))
    }

    @Test fun doesNotRestoreUnsupportedOrDifferentTrack() {
        val saved = track()
        assertNull(matchingRememberedAudioTrack(listOf(track(supported = false), track(id = "2")), saved))
    }

    @Test fun resolvesDuplicateIdsOnlyWithOriginalGroupAndIndex() {
        val saved = track(group = 2, index = 1)
        val other = track(group = 0, index = 0)
        assertEquals(saved, matchingRememberedAudioTrack(listOf(other, saved), saved))
        assertNull(matchingRememberedAudioTrack(listOf(other, track(group = 3)), saved))
    }
}
