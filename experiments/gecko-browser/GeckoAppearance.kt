package com.example.browser.gecko

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import org.mozilla.geckoview.GeckoRuntimeSettings

/** Maps Hi Browser's text-size preference onto Gecko's runtime-wide zoom. */
internal object GeckoAppearancePolicy {
    fun fontScale(textZoomPercent: Int, systemFontScale: Float): Float {
        val systemScale = systemFontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        return systemScale * textZoomPercent.coerceIn(80, 200) / 100f
    }
}

internal object GeckoAppearance {
    /**
     * Gecko applies font changes after page reload. The caller must explain this to
     * the user rather than silently reloading pages containing unfinished forms.
     * Settings are runtime-wide, not per-tab. Reapply when system font scale changes.
     */
    @MainThread fun apply(context: Context, darkMode: Boolean, textZoomPercent: Int, systemFontScale: Float = context.resources.configuration.fontScale) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val settings = GeckoRuntimeHolder.get(context).settings
        settings.automaticFontSizeAdjustment = false
        settings.fontSizeFactor = GeckoAppearancePolicy.fontScale(textZoomPercent, systemFontScale)
        settings.preferredColorScheme = if (darkMode) GeckoRuntimeSettings.COLOR_SCHEME_DARK
            else GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
    }
}
