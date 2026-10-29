package com.example.ui.screens

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicToolbarContrastTest {
    @Test fun whiteSelectedMusicTabHasBlackText() { assertEquals(Color.Black, musicSelectedLabelColor(Color.White)) }
    @Test fun blackSelectedMusicTabHasWhiteText() { assertEquals(Color.White, musicSelectedLabelColor(Color.Black)) }
}
