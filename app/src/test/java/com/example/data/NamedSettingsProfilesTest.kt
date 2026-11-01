package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
class NamedSettingsProfilesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val repository get() = PlayerPreferencesRepository(context)
    @Before fun clear() = runBlocking { context.dataStore.edit { it.clear() }; Unit }
    @Test fun playbackRoundTripDoesNotSnapshotSecretsOrTheme() = runBlocking {
        val repo = repository
        repo.setOpenSubtitlesCredentials("test-key-not-real", "test-user", "old-test-secret")
        repo.setPlaybackSpeedPercent(125)
        repo.setEqualizer("Custom", listOf(1f, 2f, 3f, 4f, 5f))
        repo.saveProfile("Movie", "playback")
        val id = repo.profilesFlow.first().single().id
        val serialized = context.dataStore.data.first()[stringPreferencesKey("named_profiles_v1")]!!
        assertFalse(serialized.contains("old-test-secret")); assertFalse(serialized.contains("opensubtitles"))
        repo.setOpenSubtitlesCredentials("new-test-key", "new-test-user", "new-test-secret")
        repo.setThemeMode(AppThemeMode.HI_PLAYER_BLUE)
        repo.setPlaybackSpeedPercent(200)
        repo.applyProfile(id)
        val settings = repo.settingsFlow.first()
        assertEquals(125, settings.playbackSpeedPercent)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), settings.equalizerBandsDb)
        assertEquals("new-test-secret", settings.openSubtitlesPassword)
        assertEquals(AppThemeMode.HI_PLAYER_BLUE, settings.themeMode)
    }
    @Test fun missingSnapshotValuesRestoreDefaultsAndThemeIsIndependent() = runBlocking {
        val repo = repository
        repo.saveProfile("Defaults", "playback")
        val defaultId = repo.profilesFlow.first().single().id
        repo.setPlaybackSpeedPercent(175)
        repo.applyProfile(defaultId)
        assertEquals(100, repo.settingsFlow.first().playbackSpeedPercent)
        repo.setThemeMode(AppThemeMode.CYAN_NEON_DARK)
        repo.saveProfile("Night", "theme")
        val themeId = repo.profilesFlow.first().first { it.kind == "theme" }.id
        repo.setThemeMode(AppThemeMode.WARM_SUNSET_LIGHT)
        repo.setPlaybackSpeedPercent(150)
        repo.applyProfile(themeId)
        assertEquals(AppThemeMode.CYAN_NEON_DARK, repo.settingsFlow.first().themeMode)
        assertEquals(150, repo.settingsFlow.first().playbackSpeedPercent)
        repo.deleteProfile(themeId)
        assertFalse(repo.profilesFlow.first().any { it.id == themeId })
    }
}
