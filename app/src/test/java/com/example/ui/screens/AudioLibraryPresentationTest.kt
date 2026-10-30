package com.example.ui.screens

import com.example.model.AudioItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AudioLibraryPresentationTest {
    private val tracks = listOf(
        AudioItem(title = "Beta", artist = "Singer", album = "Two", path = "/Music/B/b.mp3", durationMs = 2000),
        AudioItem(title = "Alpha", artist = "Other", album = "One", path = "/Music/A/a.flac", isFavorite = true, durationMs = 1000),
    )
    @Test fun filteringAndCollectionsRetainExistingBehavior() {
        assertEquals("Alpha", buildAudioLibraryPresentation(tracks, "", "Name", "All Music", null).tracks.first().title)
        assertEquals(1, buildAudioLibraryPresentation(tracks, "Singer", "Name", "All Music", null).tracks.size)
        assertEquals(listOf("A", "B"), buildAudioLibraryPresentation(tracks, "", "Name", "Folders", null).groups.keys.toList())
        assertEquals("Alpha", buildAudioLibraryPresentation(tracks, "", "Name", "Folders", "A").tracks.single().title)
    }
    @Test fun playlistsAndCustomOrderArePreserved() {
        assertEquals("Alpha", buildAudioLibraryPresentation(tracks, "", "Name", "Playlists", null).tracks.single().title)
        assertEquals(tracks, buildAudioLibraryPresentation(tracks, "", "Custom", "All Music", null).tracks)
    }
    @Test fun importedTracksWithSameMediaIdGetDifferentStableKeys() {
        assertNotEquals(audioLibraryKey(tracks[0]), audioLibraryKey(tracks[1]))
    }
    @Test fun formatBadgesDoNotInventBitrateOrHighResolutionClaims() {
        assertEquals("MP3", tracks[0].audioFormat)
        assertEquals("FLAC", tracks[1].audioFormat)
    }
}
