package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LastUsedPlaybackPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() = runBlocking {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("playback_last_used", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
    @Test fun controlsRestoreWithoutNamedProfiles() = runBlocking {
        val repository = PlayerPreferencesRepository(context)
        repository.setPlayerBrightnessPercent(64)
        repository.setPlayerVolumePercent(37)
        repository.setVolumeBoostPercent(12)
        repository.setPlayerMuted(true)
        repository.setPreferredAudioLanguage("hi")
        repository.setPreferredSubtitleTrack("en-US", true)
        val restored = PlayerPreferencesRepository(context).settingsFlow.first()
        assertEquals(64, restored.playerBrightnessPercent)
        assertEquals(37, restored.playerVolumePercent)
        assertEquals(12, restored.volumeBoostPercent)
        assertTrue(restored.playerMuted)
        assertEquals("hi", restored.preferredAudioLanguage)
        assertEquals("en-us", restored.preferredSubtitleLanguage)
        assertTrue(restored.subtitlesEnabled)
    }
    @Test fun subtitlesOffPersistsAndRetainsPreferredLanguage() = runBlocking {
        val repository = PlayerPreferencesRepository(context)
        repository.setPreferredSubtitleTrack("ar", true)
        repository.setPreferredSubtitleTrack(null, false)
        val restored = repository.settingsFlow.first()
        assertFalse(restored.subtitlesEnabled)
        assertEquals("ar", restored.preferredSubtitleLanguage)
    }
    @Test fun noVolumeOverrideExistsUntilTheUserHasUsedAPlayer() {
        assertNull(PlaybackLoudness.savedPercent(context))
        PlaybackLoudness.remember(context, 43)
        assertEquals(43, PlaybackLoudness.savedPercent(context))
        PlaybackLoudness.remember(context, 0)
        assertEquals(0, PlaybackLoudness.savedPercent(context))
    }
    @Test fun nonLanguageLabelsDoNotBecomeCrossFileTrackIndexes() {
        assertNull(normalizeTrackLanguage("und"))
        assertNull(normalizeTrackLanguage("Audio Track 2"))
        assertEquals("pt-br", normalizeTrackLanguage("pt_BR"))
    }
}
