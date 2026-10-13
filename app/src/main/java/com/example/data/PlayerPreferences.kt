package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hi_player_settings")

enum class AppThemeMode(val id: String, val displayName: String, val description: String) {
    CYAN_NEON_DARK("CYAN_NEON_DARK", "Monochrome Dark", "Background: #000000 | Accent: #FFFFFF | Text: #FFFFFF"),
    WARM_SUNSET_LIGHT("WARM_SUNSET_LIGHT", "Monochrome Light", "Background: #FFFFFF | Accent: #000000 | Text: #000000"),
    HI_PLAYER_BLUE("HI_PLAYER_BLUE", "Ice Blue", "Background: Ice Blue Gradient | Accent: Deep Blue & Cyan | Text: Dark Slate"),
    CUSTOM("CUSTOM", "Custom", "Your colors and opacity");

    companion object {
        fun fromId(id: String?): AppThemeMode {
            return when (id) {
                "WARM_SUNSET_LIGHT", "MONOCHROME_LIGHT", "SKY_BLUE_WHITE", "BLACK_WHITE_MONO", "FOREST_GREEN_WHITE", "PINK_WHITE" -> WARM_SUNSET_LIGHT
                // Retain a safe fallback for installations that previously stored the removed theme.
                "CLASSIC_RED_BLACK", "DEEP_PURPLE_GRADIENT", "GOLD_BLACK", "CRIMSON_NIGHT" -> CYAN_NEON_DARK
                "HI_PLAYER_BLUE", "LOGO_THEME" -> HI_PLAYER_BLUE
                "CUSTOM" -> CUSTOM
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
            return entries.find { it.id == id } ?: SW
        }
    }
}

/**
 * App-owned player settings persisted in DataStore. Device media volume is
 * intentionally only restored after the user starts playback; opening the app
 * or browsing the library never changes the device's current volume.
 */
data class AppPlayerSettings(
    val themeMode: AppThemeMode = AppThemeMode.WARM_SUNSET_LIGHT,
    val isFirstLaunch: Boolean = true,
    val hwAccelerationMode: HwAccelerationMode = HwAccelerationMode.HW,
    val hardwareDecoding: Boolean = true,
    val remuxUltraBufferMode: Boolean = false,
    val enableTunneling: Boolean = false,
    val enableWideColorGamut: Boolean = true,
    val enableHdrEnhance: Boolean = false,
    val backgroundPlayEnabled: Boolean = false,
    val autoPipEnabled: Boolean = false,
    val seekStepSeconds: Int = 10,
    val defaultAspectRatio: String = "FIT",
    val subtitleTextSizeSp: Int = 18,
    val subtitleTextColorHex: String = "#FFFFFF",
    val subtitleBackgroundHex: String = "#80000000",
    val subtitleFontFamily: String = "SansSerif",
    val subtitleTextColorArgb: Long = 0xFFFFFFFF,
    val subtitleBackgroundOpacityPercent: Int = 60,
    val subtitleVerticalOffsetDp: Int = 24,
    /** -1 means the player has not yet been given a brightness value. */
    val playerBrightnessPercent: Int = -1,
    /** -1 means preserve the device's current media volume for now. */
    val playerVolumePercent: Int = -1,
    val volumeBoostPercent: Int = 0,
    val playbackSpeedPercent: Int = 100,
    val equalizerPreset: String = "Flat",
    val equalizerBandsDb: List<Float> = List(5) { 0f },
    val audioDelayMs: Long = 0L,
    val subtitleOffsetMs: Long = 0L,
    val uiTextSizeSp: Int = 14,
    val customThemeTextHex: String = "#0F172A",
    val customThemeBackgroundHex: String = "#F8FAFC",
    val customThemePrimaryHex: String = "#0056B3",
    val customThemeSurfaceHex: String = "#FFFFFF",
    val customThemeTextOpacity: Int = 100,
    val customThemeBackgroundOpacity: Int = 100,
    val customThemePrimaryOpacity: Int = 100,
    val customThemeSurfaceOpacity: Int = 100,
    val nightAudioMode: Boolean = false,
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
        val PIP_CONSENT = booleanPreferencesKey("auto_pip_consent")
        val SEEK_STEP = intPreferencesKey("seek_step_seconds")
        val ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val SUBTITLE_SIZE = intPreferencesKey("subtitle_text_size")
        val SUBTITLE_COLOR = stringPreferencesKey("subtitle_text_color")
        val SUBTITLE_FONT_FAMILY = stringPreferencesKey("subtitle_font_family")
        val SUBTITLE_COLOR_ARGB = longPreferencesKey("subtitle_text_color_argb")
        val SUBTITLE_BACKGROUND_OPACITY = intPreferencesKey("subtitle_background_opacity_percent")
        val SUBTITLE_VERTICAL_OFFSET = intPreferencesKey("subtitle_vertical_offset_dp")
        val PLAYER_BRIGHTNESS = intPreferencesKey("player_brightness_percent")
        val PLAYER_VOLUME = intPreferencesKey("player_volume_percent")
        val VOLUME_BOOST = intPreferencesKey("volume_boost_percent")
        val PLAYBACK_SPEED = intPreferencesKey("playback_speed_percent")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_BAND_0 = intPreferencesKey("equalizer_band_0_centidb")
        val EQUALIZER_BAND_1 = intPreferencesKey("equalizer_band_1_centidb")
        val EQUALIZER_BAND_2 = intPreferencesKey("equalizer_band_2_centidb")
        val EQUALIZER_BAND_3 = intPreferencesKey("equalizer_band_3_centidb")
        val EQUALIZER_BAND_4 = intPreferencesKey("equalizer_band_4_centidb")
        val AUDIO_DELAY_MS = longPreferencesKey("audio_delay_ms")
        val SUBTITLE_OFFSET_MS = longPreferencesKey("subtitle_offset_ms")
        val UI_TEXT_SIZE = intPreferencesKey("ui_text_size_v2")
        val CUSTOM_TEXT = stringPreferencesKey("custom_theme_text")
        val CUSTOM_BACKGROUND = stringPreferencesKey("custom_theme_background")
        val CUSTOM_PRIMARY = stringPreferencesKey("custom_theme_primary")
        val CUSTOM_SURFACE = stringPreferencesKey("custom_theme_surface")
        val CUSTOM_TEXT_OPACITY = intPreferencesKey("custom_theme_text_opacity")
        val CUSTOM_BACKGROUND_OPACITY = intPreferencesKey("custom_theme_background_opacity")
        val CUSTOM_PRIMARY_OPACITY = intPreferencesKey("custom_theme_primary_opacity")
        val CUSTOM_SURFACE_OPACITY = intPreferencesKey("custom_theme_surface_opacity")
        val NIGHT_AUDIO = booleanPreferencesKey("night_audio_mode")
        val OS_API_KEY = stringPreferencesKey("opensubtitles_api_key")
        val OS_USERNAME = stringPreferencesKey("opensubtitles_username")
        val OS_PASSWORD = stringPreferencesKey("opensubtitles_password")
    }

    val settingsFlow: Flow<AppPlayerSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.WARM_SUNSET_LIGHT.id
        val hwStr = prefs[Keys.HW_ACCEL_MODE] ?: HwAccelerationMode.HW.id
        val isFirst = prefs[Keys.IS_FIRST_LAUNCH] ?: true
        val hwMode = HwAccelerationMode.fromId(hwStr)
        val equalizerBands = listOf(
            prefs[Keys.EQUALIZER_BAND_0] ?: 0,
            prefs[Keys.EQUALIZER_BAND_1] ?: 0,
            prefs[Keys.EQUALIZER_BAND_2] ?: 0,
            prefs[Keys.EQUALIZER_BAND_3] ?: 0,
            prefs[Keys.EQUALIZER_BAND_4] ?: 0,
        ).map { it / 100f }

        AppPlayerSettings(
            themeMode = AppThemeMode.fromId(themeStr),
            isFirstLaunch = isFirst,
            hwAccelerationMode = hwMode,
            hardwareDecoding = hwMode != HwAccelerationMode.SW,
            remuxUltraBufferMode = prefs[Keys.REMUX_BUFFER] ?: false,
            enableTunneling = (hwMode == HwAccelerationMode.HW_PLUS) && (prefs[Keys.TUNNELING] ?: false),
            enableWideColorGamut = prefs[Keys.WIDE_COLOR_GAMUT] ?: false,
            enableHdrEnhance = prefs[Keys.HDR_ENHANCE] ?: false,
            backgroundPlayEnabled = prefs[Keys.BG_PLAY] ?: false,
            autoPipEnabled = (prefs[Keys.AUTO_PIP] ?: false) && (prefs[Keys.PIP_CONSENT] ?: false),
            seekStepSeconds = prefs[Keys.SEEK_STEP] ?: 10,
            defaultAspectRatio = prefs[Keys.ASPECT_RATIO] ?: "FIT",
            subtitleTextSizeSp = prefs[Keys.SUBTITLE_SIZE] ?: 18,
            subtitleTextColorHex = prefs[Keys.SUBTITLE_COLOR] ?: "#FFFFFF",
            subtitleFontFamily = prefs[Keys.SUBTITLE_FONT_FAMILY] ?: "SansSerif",
            subtitleTextColorArgb = prefs[Keys.SUBTITLE_COLOR_ARGB] ?: 0xFFFFFFFF,
            subtitleBackgroundOpacityPercent = (prefs[Keys.SUBTITLE_BACKGROUND_OPACITY] ?: 60).coerceIn(0, 100),
            subtitleVerticalOffsetDp = (prefs[Keys.SUBTITLE_VERTICAL_OFFSET] ?: 24).coerceIn(0, 240),
            playerBrightnessPercent = (prefs[Keys.PLAYER_BRIGHTNESS] ?: -1).coerceIn(-1, 100),
            playerVolumePercent = (prefs[Keys.PLAYER_VOLUME] ?: -1).coerceIn(-1, 100),
            volumeBoostPercent = (prefs[Keys.VOLUME_BOOST] ?: 0).coerceIn(0, 100),
            playbackSpeedPercent = (prefs[Keys.PLAYBACK_SPEED] ?: 100).coerceIn(25, 400),
            equalizerPreset = prefs[Keys.EQUALIZER_PRESET] ?: "Flat",
            equalizerBandsDb = equalizerBands,
            audioDelayMs = (prefs[Keys.AUDIO_DELAY_MS] ?: 0L).coerceIn(-10_000L, 10_000L),
            subtitleOffsetMs = (prefs[Keys.SUBTITLE_OFFSET_MS] ?: 0L).coerceIn(-10_000L, 10_000L),
            uiTextSizeSp = prefs[Keys.UI_TEXT_SIZE] ?: 14,
            customThemeTextHex = prefs[Keys.CUSTOM_TEXT] ?: "#0F172A",
            customThemeBackgroundHex = prefs[Keys.CUSTOM_BACKGROUND] ?: "#F8FAFC",
            customThemePrimaryHex = prefs[Keys.CUSTOM_PRIMARY] ?: "#0056B3",
            customThemeSurfaceHex = prefs[Keys.CUSTOM_SURFACE] ?: "#FFFFFF",
            customThemeTextOpacity = prefs[Keys.CUSTOM_TEXT_OPACITY] ?: 100,
            customThemeBackgroundOpacity = prefs[Keys.CUSTOM_BACKGROUND_OPACITY] ?: 100,
            customThemePrimaryOpacity = prefs[Keys.CUSTOM_PRIMARY_OPACITY] ?: 100,
            customThemeSurfaceOpacity = prefs[Keys.CUSTOM_SURFACE_OPACITY] ?: 100,
            nightAudioMode = prefs[Keys.NIGHT_AUDIO] ?: false,
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
            it[Keys.HW_ACCEL_MODE] = if (enabled) HwAccelerationMode.HW.id else HwAccelerationMode.SW.id
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
        context.dataStore.edit {
            it[Keys.AUTO_PIP] = enabled
            it[Keys.PIP_CONSENT] = enabled
        }
    }

    suspend fun setSeekStepSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.SEEK_STEP] = seconds }
    }

    suspend fun setDefaultAspectRatio(mode: String) {
        context.dataStore.edit { it[Keys.ASPECT_RATIO] = mode }
    }

    suspend fun setSubtitleTextSize(sizeSp: Int) {
        context.dataStore.edit { it[Keys.SUBTITLE_SIZE] = sizeSp.coerceIn(12, 48) }
    }

    suspend fun setSubtitleStyle(
        fontSizeSp: Int,
        fontFamily: String,
        textColorArgb: Long,
        backgroundOpacityPercent: Int,
        verticalOffsetDp: Int,
    ) {
        context.dataStore.edit {
            it[Keys.SUBTITLE_SIZE] = fontSizeSp.coerceIn(12, 48)
            it[Keys.SUBTITLE_FONT_FAMILY] = fontFamily.ifBlank { "SansSerif" }
            it[Keys.SUBTITLE_COLOR_ARGB] = textColorArgb
            it[Keys.SUBTITLE_COLOR] = "#%08X".format(textColorArgb)
            it[Keys.SUBTITLE_BACKGROUND_OPACITY] = backgroundOpacityPercent.coerceIn(0, 100)
            it[Keys.SUBTITLE_VERTICAL_OFFSET] = verticalOffsetDp.coerceIn(0, 240)
        }
    }

    suspend fun setPlayerBrightnessPercent(percent: Int) {
        context.dataStore.edit { it[Keys.PLAYER_BRIGHTNESS] = percent.coerceIn(1, 100) }
    }

    suspend fun setPlayerVolumePercent(percent: Int) {
        context.dataStore.edit { it[Keys.PLAYER_VOLUME] = percent.coerceIn(0, 100) }
    }

    suspend fun setVolumeBoostPercent(percent: Int) {
        context.dataStore.edit { it[Keys.VOLUME_BOOST] = percent.coerceIn(0, 100) }
    }

    suspend fun setPlaybackSpeedPercent(percent: Int) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = percent.coerceIn(25, 400) }
    }

    suspend fun setEqualizer(preset: String, bandsDb: List<Float>) {
        val normalizedBands = List(5) { index -> bandsDb.getOrElse(index) { 0f }.coerceIn(-12f, 12f) }
        context.dataStore.edit {
            it[Keys.EQUALIZER_PRESET] = preset.trim().ifBlank { "Flat" }
            it[Keys.EQUALIZER_BAND_0] = (normalizedBands[0] * 100f).toInt()
            it[Keys.EQUALIZER_BAND_1] = (normalizedBands[1] * 100f).toInt()
            it[Keys.EQUALIZER_BAND_2] = (normalizedBands[2] * 100f).toInt()
            it[Keys.EQUALIZER_BAND_3] = (normalizedBands[3] * 100f).toInt()
            it[Keys.EQUALIZER_BAND_4] = (normalizedBands[4] * 100f).toInt()
        }
    }

    suspend fun setAudioDelayMs(delayMs: Long) {
        context.dataStore.edit { it[Keys.AUDIO_DELAY_MS] = delayMs.coerceIn(-10_000L, 10_000L) }
    }

    suspend fun setSubtitleOffsetMs(offsetMs: Long) {
        context.dataStore.edit { it[Keys.SUBTITLE_OFFSET_MS] = offsetMs.coerceIn(-10_000L, 10_000L) }
    }

    suspend fun setUiTextSize(sizeSp: Int) {
        context.dataStore.edit { it[Keys.UI_TEXT_SIZE] = sizeSp.coerceIn(12, 24) }
    }

    suspend fun setCustomThemeColors(
        textHex: String,
        backgroundHex: String,
        primaryHex: String,
        surfaceHex: String,
        textOpacity: Int,
        backgroundOpacity: Int,
        primaryOpacity: Int,
        surfaceOpacity: Int,
    ) {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = AppThemeMode.CUSTOM.id
            it[Keys.CUSTOM_TEXT] = textHex
            it[Keys.CUSTOM_BACKGROUND] = backgroundHex
            it[Keys.CUSTOM_PRIMARY] = primaryHex
            it[Keys.CUSTOM_SURFACE] = surfaceHex
            it[Keys.CUSTOM_TEXT_OPACITY] = textOpacity.coerceIn(0, 100)
            it[Keys.CUSTOM_BACKGROUND_OPACITY] = backgroundOpacity.coerceIn(0, 100)
            it[Keys.CUSTOM_PRIMARY_OPACITY] = primaryOpacity.coerceIn(0, 100)
            it[Keys.CUSTOM_SURFACE_OPACITY] = surfaceOpacity.coerceIn(0, 100)
        }
    }

    suspend fun setNightAudioMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NIGHT_AUDIO] = enabled }
    }

    suspend fun setOpenSubtitlesCredentials(apiKey: String, username: String, password: String) {
        context.dataStore.edit {
            it[Keys.OS_API_KEY] = apiKey
            it[Keys.OS_USERNAME] = username
            it[Keys.OS_PASSWORD] = password
        }
    }
}
