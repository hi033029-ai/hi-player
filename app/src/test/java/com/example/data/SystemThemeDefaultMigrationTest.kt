package com.example.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SystemThemeDefaultMigrationTest {
    private val theme = stringPreferencesKey("theme_mode")
    private val custom = stringPreferencesKey("custom_theme_primary")

    @Test fun existingInstallSwitchesToSystemWithoutDeletingCustomColors() = runTest {
        val before = preferencesOf(theme to "CUSTOM", custom to "#EC4899")
        assertTrue(SystemThemeDefaultMigration.shouldMigrate(before))
        val after = SystemThemeDefaultMigration.migrate(before)
        assertEquals("SYSTEM", after[theme])
        assertEquals("#EC4899", after[custom])
        assertFalse(SystemThemeDefaultMigration.shouldMigrate(after))
    }
    @Test fun laterExplicitThemeChoiceIsNotResetOnEveryLaunch() = runTest {
        val after = preferencesOf(theme to "HI_PLAYER_BLUE", booleanPreferencesKey("system_theme_default_v2") to true)
        assertFalse(SystemThemeDefaultMigration.shouldMigrate(after))
        assertEquals("HI_PLAYER_BLUE", after[theme])
    }    @Test fun previousMigrationDoesNotLeaveAnOldBlueOverrideActive() = runTest {
        val before = preferencesOf(theme to "HI_PLAYER_BLUE", booleanPreferencesKey("system_theme_default_v1") to true)
        assertTrue(SystemThemeDefaultMigration.shouldMigrate(before))
        val after = SystemThemeDefaultMigration.migrate(before)
        assertEquals("SYSTEM", after[theme])
        assertFalse(SystemThemeDefaultMigration.shouldMigrate(after))
    }

}
