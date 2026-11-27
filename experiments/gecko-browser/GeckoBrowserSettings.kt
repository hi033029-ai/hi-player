package com.example.browser.gecko

import android.content.Context
import androidx.annotation.MainThread
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import com.example.ui.theme.AppDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.theme.LocalHiPalette
import kotlin.math.roundToInt

/** Applies only settings which the experimental Gecko implementation actually supports. */
internal class GeckoBrowserSettings(
    initialDark: Boolean,
    initialZoom: Int,
    initialPopupBlock: Boolean,
    private val applyAppearance: (Boolean, Int, Float) -> Unit,
    private val saveAppearance: (Boolean, Int) -> Unit,
    private val savePopupBlock: (Boolean) -> Unit,
    initialAdBlock: Boolean = true,
    initialPageSkipper: Boolean = true,
    initialYoutubeAdSkip: Boolean = true,
    initialVideoHandoff: Boolean = true,
    private val saveContentPreferences: (Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
) {
    var dark by mutableStateOf(initialDark)
        private set
    var zoom by mutableIntStateOf(initialZoom.coerceIn(80, 200))
        private set
    var popupBlock by mutableStateOf(initialPopupBlock)
        private set
    var adBlock by mutableStateOf(initialAdBlock)
        private set
    var pageSkipper by mutableStateOf(initialPageSkipper)
        private set
    var youtubeAdSkip by mutableStateOf(initialYoutubeAdSkip)
        private set
    var videoHandoff by mutableStateOf(initialVideoHandoff)
        private set
    var contentNeedsReload by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var scale = 1f

    @MainThread fun applySystemScale(value: Float, appDark: Boolean = dark) {
        dark = appDark
        val newScale = value.takeIf { it.isFinite() && it > 0f } ?: 1f
        scale = newScale
        failed = runCatching { applyAppearance(dark, zoom, scale) }.isFailure
    }

    @MainThread fun setAppearance(newDark: Boolean, newZoom: Int) {
        val bounded = newZoom.coerceIn(80, 200)
        val previousDark = dark
        val previousZoom = zoom
        try {
            applyAppearance(newDark, bounded, scale)
            saveAppearance(newDark, bounded)
            dark = newDark
            zoom = bounded
            failed = false
        } catch (_: Exception) {
            runCatching { applyAppearance(previousDark, previousZoom, scale) }
            failed = true
        }
    }
    @MainThread fun changePopupBlock(value: Boolean) {
        try { savePopupBlock(value); popupBlock = value; failed = false }
        catch (_: Exception) { failed = true }
    }
    @MainThread fun changeAdBlock(value: Boolean) = updateContent(value, pageSkipper, youtubeAdSkip, videoHandoff, requiresReload = true)
    @MainThread fun changePageSkipper(value: Boolean) = updateContent(adBlock, value, youtubeAdSkip, videoHandoff, requiresReload = true)
    @MainThread fun changeYoutubeAdSkip(value: Boolean) = updateContent(adBlock, pageSkipper, value, videoHandoff, requiresReload = true)
    @MainThread fun changeVideoHandoff(value: Boolean) = updateContent(adBlock, pageSkipper, youtubeAdSkip, value, requiresReload = false)
    @MainThread fun markContentApplied() { contentNeedsReload = false }

    private fun updateContent(adBlock: Boolean, pageSkipper: Boolean, youtubeAdSkip: Boolean, videoHandoff: Boolean, requiresReload: Boolean) {
        try {
            saveContentPreferences(adBlock, pageSkipper, youtubeAdSkip, videoHandoff)
            this.adBlock = adBlock
            this.pageSkipper = pageSkipper
            this.youtubeAdSkip = youtubeAdSkip
            this.videoHandoff = videoHandoff
            if (requiresReload) contentNeedsReload = true
            failed = false
        } catch (_: Exception) { failed = true }
    }
}

internal fun createGeckoBrowserSettings(context: Context, store: OrbitBrowserStore): GeckoBrowserSettings =
    GeckoBrowserSettings(store.darkMode, store.textZoom, store.popupBlock,
        applyAppearance = { dark, zoom, scale -> GeckoAppearance.apply(context, dark, zoom, scale) },
        saveAppearance = { dark, zoom -> store.darkMode = dark; store.textZoom = zoom },
        savePopupBlock = { store.popupBlock = it },
        initialAdBlock = store.adBlock,
        initialPageSkipper = store.pageSkipper,
        initialYoutubeAdSkip = store.youtubeAdSkip,
        initialVideoHandoff = store.videoHandoff,
        saveContentPreferences = { adBlock, pageSkipper, youtubeAdSkip, videoHandoff ->
            store.adBlock = adBlock
            store.pageSkipper = pageSkipper
            store.youtubeAdSkip = youtubeAdSkip
            store.videoHandoff = videoHandoff
        },
    )

/** A compact panel for supported settings; other browser settings remain host-owned. */
@Composable
internal fun GeckoAppearanceDialog(
    settings: GeckoBrowserSettings,
    canReloadCurrentPage: Boolean = true,
    onReloadCurrentPage: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val autofillPrefs = remember(context) { GeckoPasswordAutofill.preferences(context) }
    var passwordAutofill by remember(autofillPrefs) {
        mutableStateOf(autofillPrefs.getBoolean(GeckoPasswordAutofill.ENABLED, false))
    }
    var autofillSettingsUnavailable by remember { mutableStateOf(false) }
    var preview by remember(settings, settings.zoom) { mutableIntStateOf(settings.zoom) }
    val palette = LocalHiPalette.current
    AppDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Browser settings", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = palette.textPrimary)
                    TextButton(onClick = onDismiss) { Text("Done", color = palette.primary) }
                }
                HorizontalDivider(color = palette.surfaceBorder.copy(alpha = .5f))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GeckoSettingsHeading("Appearance")
                    Text("Browser colors follow the app theme.")
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextFields, null, tint = palette.primary, modifier = Modifier.size(22.dp))
                            AnimatedContent(targetState = preview, label = "webpage-text-preview") { value ->
                                Text("  Webpage text · $value%", color = palette.textPrimary)
                            }
                        }
                        Slider(value = preview.toFloat(), valueRange = 80f..200f, steps = 11,
                            onValueChange = { preview = ((it / 10f).roundToInt() * 10).coerceIn(80, 200) },
                            onValueChangeFinished = {
                                val previous = settings.zoom
                                settings.setAppearance(settings.dark, preview)
                                preview = settings.zoom
                                if (settings.zoom != previous && canReloadCurrentPage) onReloadCurrentPage()
                            },
                            modifier = Modifier.semantics { contentDescription = "Webpage text size" })
                        TextButton(enabled = settings.zoom != 100 || preview != 100,
                            onClick = {
                                val previous = settings.zoom
                                settings.setAppearance(settings.dark, 100)
                                preview = settings.zoom
                                if (settings.zoom != previous && canReloadCurrentPage) onReloadCurrentPage()
                            }) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)); Text("  Reset to 100%")
                        }
                    }
                    GeckoSettingsHeading("Privacy and content")
                    if (GeckoPasswordAutofill.available()) {
                        GeckoSettingSwitchRow(
                            "Password autofill",
                            "Use your Android password manager to save or fill logins on secure, regular tabs",
                            Icons.Default.TextFields, passwordAutofill, "Password autofill",
                        ) { enabled ->
                            autofillPrefs.edit().putBoolean(GeckoPasswordAutofill.ENABLED, enabled).apply()
                            passwordAutofill = enabled
                        }
                        TextButton(onClick = { autofillSettingsUnavailable = !GeckoPasswordAutofill.chooseProvider(context) }) {
                            Text("Choose password manager")
                        }
                        if (autofillSettingsUnavailable) Text("Choose an autofill service in Android Settings.")
                    }
                    GeckoSettingSwitchRow("Ad and tracker blocking", "Block common ad and tracker requests", Icons.Default.Block,
                        settings.adBlock, "Ad and tracker blocking", settings::changeAdBlock)
                    GeckoSettingSwitchRow("Block unwanted pop-ups", "Review new windows before they open", Icons.Default.Block,
                        settings.popupBlock, "Block pop-ups", settings::changePopupBlock)
                    GeckoSettingSwitchRow("Skip page transitions", "Try to continue through supported pages", Icons.Default.Refresh,
                        settings.pageSkipper, "Skip page transitions", settings::changePageSkipper)
                    GeckoSettingsHeading("Videos")
                    GeckoSettingSwitchRow("Detect website videos", "Find supported videos for Play in Hi Player", Icons.Default.TextFields,
                        settings.videoHandoff, "Detect website videos", settings::changeVideoHandoff)
                    if (settings.contentNeedsReload) {
                        Text("Reload the current page to apply blocking and script changes. Unsent page input may be lost.", color = palette.textSecondary)
                        TextButton(enabled = canReloadCurrentPage, onClick = { settings.markContentApplied(); onReloadCurrentPage() }) { Text("Reload current page") }
                    }
                    AnimatedVisibility(visible = settings.failed, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                        Text("Could not apply that setting. Your previous setting was restored.", color = com.example.ui.theme.HiErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeckoSettingsHeading(title: String) {
    val palette = LocalHiPalette.current
    Text(title, Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium, color = palette.primary)
}

@Composable
private fun GeckoSettingSwitchRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalHiPalette.current
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (checked) palette.primary else palette.textSecondary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = palette.textPrimary)
            Text(description, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
