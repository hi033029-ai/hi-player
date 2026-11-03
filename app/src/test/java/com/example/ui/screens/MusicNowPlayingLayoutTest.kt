package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.model.AudioItem
import com.example.ui.theme.HiPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class MusicNowPlayingLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val track = AudioItem(123, Uri.parse("content://test/audio/123"), "Current song", "Artist", "Album", 200000, 4096, "/test/song.mp3", "audio/mpeg")
    @Test fun metadataSitsAboveLyricsAndNoLibraryTabsAreInsideExpandedLayout() {
        compose.setContent { HiPlayerTheme { Box(Modifier.size(360.dp, 560.dp)) {
            FullScreenAudioPlayerScreen(track, true, 10000, 200000, "Flat", null,
                lyricsEnabled = true, lyricsText = "First lyric line\nSecond lyric line",
                onTogglePlay = {}, onNext = {}, onPrevious = {}, onSeek = {}, onOpenEq = {},
                onOpenSubtitleSearch = {}, onBack = {}, onCancel = {})
        } } }
        compose.onNodeWithText("Current song").assertIsDisplayed()
        compose.onNodeWithText("First lyric line").assertIsDisplayed()
        val header = compose.onNodeWithTag("audio_song_header").fetchSemanticsNode().boundsInRoot
        val lyrics = compose.onNodeWithTag("audio_lyrics_area").fetchSemanticsNode().boundsInRoot
        assertTrue(header.bottom <= lyrics.top)
        compose.onNodeWithText("Playlists").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next Track").assertIsDisplayed()
    }
    @Test fun miniPlayerBodyAndArrowExpandWhileTransportDoesNotExpand() {
        var expanded = 0; var played = false
        compose.setContent { HiPlayerTheme {
            WavyNowPlayingBottomSheet(track, false, 0, 200000, "Flat", null,
                onTogglePlay = { played = true }, onNext = {}, onPrevious = {}, onSeek = {},
                onOpenEq = {}, onOpenSubtitleSearch = {}, onCancel = {}, onExpandFullScreen = { expanded++ })
        } }
        compose.onNodeWithTag("expand_audio_body").performClick()
        compose.runOnIdle { assertEquals("body click", 1, expanded) }
        compose.onNodeWithTag("expand_audio_sheet").performClick()
        compose.runOnIdle { assertEquals("arrow click", 2, expanded) }
        compose.onNodeWithContentDescription("Play").performClick()
        compose.runOnIdle { assertEquals(2, expanded); assertTrue(played) }
    }
    @Test fun miniPlayerShowsOnlyTheCurrentTimedLineAndCompactSeekBar() {
        val lyric = mutableStateOf("First line")
        val index = mutableStateOf(0)
        val enabled = mutableStateOf(true)
        compose.setContent { HiPlayerTheme {
            WavyNowPlayingBottomSheet(track, true, 1000, 200000, "Flat", lyric.value,
                onTogglePlay = {}, onNext = {}, onPrevious = {}, onSeek = {}, onOpenEq = {},
                onOpenSubtitleSearch = {}, onCancel = {}, lyricsEnabled = enabled.value,
                activeLyricLineIndex = index.value)
        } }
        compose.onNodeWithTag("mini_player_lyric").assertIsDisplayed().assertTextEquals("First line")
        compose.onNodeWithTag("audio_neon_seekbar").assertIsDisplayed()
        compose.runOnIdle { lyric.value = "Next line"; index.value = 1 }
        compose.onNodeWithTag("mini_player_lyric").assertTextEquals("Next line")
        compose.runOnIdle { index.value = -1 }
        compose.onNodeWithTag("mini_player_lyric").assertDoesNotExist()
        compose.runOnIdle { index.value = 0; enabled.value = false }
        compose.onNodeWithTag("mini_player_lyric").assertDoesNotExist()
    }

}
