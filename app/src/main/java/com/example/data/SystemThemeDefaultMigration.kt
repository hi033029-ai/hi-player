package com.example.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** User-requested one-time reset to System for upgrades as well as fresh installations. */
internal object SystemThemeDefaultMigration : DataMigration<Preferences> {
    private val applied = booleanPreferencesKey("system_theme_default_v1")
    private val theme = stringPreferencesKey("theme_mode")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[applied] != true

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        this[theme] = AppThemeMode.SYSTEM.id
        this[applied] = true
    }

    override suspend fun cleanUp() = Unit
}
