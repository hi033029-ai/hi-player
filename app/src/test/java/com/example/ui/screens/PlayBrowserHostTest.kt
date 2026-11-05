package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBrowserHostTest {
    @Test fun youtubeHostsUseDomainBoundaries() {
        listOf("youtube.com", "m.youtube.com", "YOUTUBE.COM.", "youtube-nocookie.com", "www.youtube-nocookie.com", "youtu.be").forEach { assertTrue(it, isYoutubeHost(it)) }
        listOf("notyoutube.com", "youtube.com.example.org", "example.org", "", "youtube-nocookie.com.evil").forEach { assertFalse(it, isYoutubeHost(it)) }
        assertFalse(isYoutubeHost(null))
    }
}
