package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hi_player_settings")

enum class AppThemeMode(val id: String, val displayName: String, val description: String) {
    CYAN_NEON_DARK("CYAN_NEON_DARK", "Monochrome Dark", "Background: #000000 | Accent: #FFFFFF | Text: #FFFFFF"),
    WARM_SUNSET_LIGHT("WARM_SUNSET_LIGHT", "Monochrome Light", "Background: #FFFFFF | Accent: #000000 | Text: #000000"),
    CLASSIC_RED_BLACK("CLASSIC_RED_BLACK", "Netflix Classic", "Background: #000000 | Accent: #E10600 | Text: #E10600"),
    HI_PLAYER_BLUE("HI_PLAYER_BLUE", "Hi Player Logo", "Background: Ice Blue | Accent: Deep Blue & Cyan | Text: Dark Slate");

    companion object {
        fun fromId(id: String?): AppThemeMode {
            return when (id) {
                "WARM_SUNSET_LIGHT", "MONOCHROME_LIGHT", "SKY_BLUE_WHITE", "BLACK_WHITE_MONO", "FOREST_GREEN_WHITE", "PINK_WHITE" -> WARM_SUNSET_LIGHT
                "CLASSIC_RED_BLACK", "DEEP_PURPLE_GRADIENT", "GOLD_BLACK", "CRIMSON_NIGHT" -> CLASSIC_RED_BLACK
                "HI_PLAYER_BLUE", "LOGO_THEME" -> HI_PLAYER_BLUE
                else -> entries.find { it.id == id } ?: CYAN_NEON_DARK
            }
        }
    }
}

enum class HwAccelerationMode(val id: String, val displayName: String, val description: String) {
    HW_PLUS("HW_PLUS", "Hardware (HW+)", "Ultra-low latency GPU decoding with tunneling for 4K 60fps HDR"),
    HW("HW", "Hardware (HW)", "Standard MediaCodec hardware acceleration"),
    SW("SW", "Software (SW)", "CPU-based fallback software decoding");

    companion object {
        fun fromId(id: String?): HwAccelerationMode {
            return entries.find { it.id == id } ?: HW_PLUS
        }
    }
}

data class AppPlayerSettings(
    val themeMode: AppThemeMode = AppThemeMode.WARM_SUNSET_LIGHT,
    val isFirstLaunch: Boolean = true,
    val hwAccelerationMode: HwAccelerationMode = HwAccelerationMode.HW_PLUS,
    val hardwareDecoding: Boolean = true,
    val remuxUltraBufferMode: Boolean = true, // 128MB buffer for 4K UHD Blu-ray
    val enableTunneling: Boolean = false,
    val enableWideColorGamut: Boolean = true, // Fix 4K dull colors
    val enableHdrEnhance: Boolean = true, // Fix 4K color washed out/distortion
    val backgroundPlayEnabled: Boolean = true,
    val autoPipEnabled: Boolean = true,
    val seekStepSeconds: Int = 10,
    val defaultAspectRatio: String = "FIT",
    val subtitleTextSizeSp: Int = 18,
    val uiTextSizeSp: Int = 16,
    val subtitleTextColorHex: String = "#FFFFFF",
    val subtitleBackgroundHex: String = "#80000000",
    val nightAudioMode: Boolean = false,
    val volumeBoostPercent: Int = 0, // 0 to 100 boost on top of 100%
    val tmdbApiKey: String = "",
    val openSubtitlesApiKey: String = "",
    val openSubtitlesUsername: String = "",
    val openSubtitlesPassword: String = ""
)

class PlayerPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val HW_ACCEL_MODE = stringPreferencesKey("hw_accel_mode")
        val HW_DECODING = booleanPreferencesKey("hw_decoding")
        val REMUX_BUFFER = booleanPreferencesKey("remux_ultra_buffer")
        val TUNNELING = booleanPreferencesKey("enable_tunneling")
        val WIDE_COLOR_GAMUT = booleanPreferencesKey("wide_color_gamut")
        val HDR_ENHANCE = booleanPreferencesKey("hdr_enhance")
        val BG_PLAY = booleanPreferencesKey("background_play")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val SEEK_STEP = intPreferencesKey("seek_step_seconds")
        val ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val SUBTITLE_SIZE = intPreferencesKey("subtitle_text_size")
        val UI_TEXT_SIZE = intPreferencesKey("ui_text_size")
        val SUBTITLE_COLOR = stringPreferencesKey("subtitle_text_color")
        val NIGHT_AUDIO = booleanPreferencesKey("night_audio_mode")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val OS_API_KEY = stringPreferencesKey("opensubtitles_api_key")
        val OS_USERNAME = stringPreferencesKey("opensubtitles_username")
        val OS_PASSWORD = stringPreferencesKey("opensubtitles_password")
    }

    val settingsFlow: Flow<AppPlayerSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.WARM_SUNSET_LIGHT.id
        val hwStr = prefs[Keys.HW_ACCEL_MODE] ?: HwAccelerationMode.HW_PLUS.id
        val isFirst = prefs[Keys.IS_FIRST_LAUNCH] ?: true
        val hwMode = HwAccelerationMode.fromId(hwStr)

        AppPlayerSettings(
            themeMode = AppThemeMode.fromId(themeStr),
            isFirstLaunch = isFirst,
            hwAccelerationMode = hwMode,
            hardwareDecoding = hwMode != HwAccelerationMode.SW,
            remuxUltraBufferMode = prefs[Keys.REMUX_BUFFER] ?: true,
            enableTunneling = (hwMode == HwAccelerationMode.HW_PLUS) && (prefs[Keys.TUNNELING] ?: false),
            enableWideColorGamut = prefs[Keys.WIDE_COLOR_GAMUT] ?: true,
            enableHdrEnhance = prefs[Keys.HDR_ENHANCE] ?: true,
            backgroundPlayEnabled = prefs[Keys.BG_PLAY] ?: true,
            autoPipEnabled = prefs[Keys.AUTO_PIP] ?: true,
            seekStepSeconds = prefs[Keys.SEEK_STEP] ?: 10,
            defaultAspectRatio = prefs[Keys.ASPECT_RATIO] ?: "FIT",
            subtitleTextSizeSp = prefs[Keys.SUBTITLE_SIZE] ?: 18,
            uiTextSizeSp = prefs[Keys.UI_TEXT_SIZE] ?: 16,
            subtitleTextColorHex = prefs[Keys.SUBTITLE_COLOR] ?: "#FFFFFF",
            nightAudioMode = prefs[Keys.NIGHT_AUDIO] ?: false,
            tmdbApiKey = prefs[Keys.TMDB_API_KEY] ?: "",
            openSubtitlesApiKey = prefs[Keys.OS_API_KEY] ?: "",
            openSubtitlesUsername = prefs[Keys.OS_USERNAME] ?: "",
            openSubtitlesPassword = prefs[Keys.OS_PASSWORD] ?: ""
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.id }
    }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { it[Keys.IS_FIRST_LAUNCH] = false }
    }

    suspend fun setHwAccelerationMode(mode: HwAccelerationMode) {
        context.dataStore.edit {
            it[Keys.HW_ACCEL_MODE] = mode.id
            it[Keys.HW_DECODING] = (mode != HwAccelerationMode.SW)
            it[Keys.TUNNELING] = (mode == HwAccelerationMode.HW_PLUS)
        }
    }

    suspend fun setHardwareDecoding(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.HW_DECODING] = enabled
            if (!enabled) {
                it[Keys.HW_ACCEL_MODE] = HwAccelerationMode.SW.id
            } else {
                it[Keys.HW_ACCEL_MODE] = HwAccelerationMode.HW.id
            }
        }
    }

    suspend fun setRemuxUltraBufferMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMUX_BUFFER] = enabled }
    }

    suspend fun setTunneling(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TUNNELING] = enabled }
    }

    suspend fun setWideColorGamut(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIDE_COLOR_GAMUT] = enabled }
    }

    suspend fun setHdrEnhance(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HDR_ENHANCE] = enabled }
    }

    suspend fun setBackgroundPlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BG_PLAY] = enabled }
    }

    suspend fun setAutoPip(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PIP] = enabled }
    }

    suspend fun setSeekStepSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.SEEK_STEP] = seconds }
    }

    suspend fun setDefaultAspectRatio(mode: String) {
        context.dataStore.edit { it[Keys.ASPECT_RATIO] = mode }
    }

    suspend fun setSubtitleTextSize(sizeSp: Int) {
        context.dataStore.edit { it[Keys.SUBTITLE_SIZE] = sizeSp }
    }

    suspend fun setUiTextSize(sizeSp: Int) {
        context.dataStore.edit { it[Keys.UI_TEXT_SIZE] = sizeSp.coerceIn(12, 24) }
    }

    suspend fun setNightAudioMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NIGHT_AUDIO] = enabled }
    }

    suspend fun setTmdbApiKey(key: String) {
        context.dataStore.edit { it[Keys.TMDB_API_KEY] = key }
    }

    suspend fun setOpenSubtitlesCredentials(apiKey: String, username: String, password: String) {
        context.dataStore.edit {
            it[Keys.OS_API_KEY] = apiKey
            it[Keys.OS_USERNAME] = username
            it[Keys.OS_PASSWORD] = password
        }
    }
}
