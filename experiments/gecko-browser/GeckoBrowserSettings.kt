package com.example.browser.gecko

import android.content.Context
import androidx.annotation.MainThread
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
) {
    var dark by mutableStateOf(initialDark)
        private set
    var zoom by mutableIntStateOf(initialZoom.coerceIn(80, 200))
        private set
    var popupBlock by mutableStateOf(initialPopupBlock)
        private set
    var failed by mutableStateOf(false)
        private set
    private var scale = 1f

    @MainThread fun applySystemScale(value: Float) {
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
}

internal fun createGeckoBrowserSettings(context: Context, store: OrbitBrowserStore): GeckoBrowserSettings =
    GeckoBrowserSettings(store.darkMode, store.textZoom, store.popupBlock,
        applyAppearance = { dark, zoom, scale -> GeckoAppearance.apply(context, dark, zoom, scale) },
        saveAppearance = { dark, zoom -> store.darkMode = dark; store.textZoom = zoom },
        savePopupBlock = { store.popupBlock = it },
    )

/** A compact panel for supported settings; other browser settings remain host-owned. */
@Composable
internal fun GeckoAppearanceDialog(settings: GeckoBrowserSettings, onDismiss: () -> Unit) {
    var preview by remember(settings, settings.zoom) { mutableIntStateOf(settings.zoom) }
    val palette = LocalHiPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = palette.surface,
        title = { Text("Appearance and pop-ups", color = palette.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Changes save automatically and apply to all tabs.", color = palette.textSecondary)
                GeckoSettingSwitchRow("Dark web pages", "Use a dark color scheme on compatible sites", Icons.Default.DarkMode,
                    settings.dark, "Dark web pages") { settings.setAppearance(it, settings.zoom) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surfaceElevated.copy(alpha = .45f)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, null, tint = palette.primary, modifier = Modifier.size(22.dp))
                        AnimatedContent(targetState = preview, label = "webpage-text-preview") { value ->
                            Text("  Webpage text · $value%", color = palette.textPrimary)
                        }
                    }
                    Slider(value = preview.toFloat(), valueRange = 80f..200f, steps = 11,
                        onValueChange = { preview = ((it / 10f).roundToInt() * 10).coerceIn(80, 200) },
                        onValueChangeFinished = { settings.setAppearance(settings.dark, preview); preview = settings.zoom },
                        modifier = Modifier.semantics { contentDescription = "Webpage text size" })
                    TextButton(enabled = settings.zoom != 100 || preview != 100,
                        onClick = { settings.setAppearance(settings.dark, 100); preview = settings.zoom }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)); Text("  Reset to 100%")
                    }
                }
                Text("Reload pages to apply text size. Unsent input may be lost when reloading.", color = palette.textSecondary)
                GeckoSettingSwitchRow("Block pop-ups", "Allow new windows only after you tap a link", Icons.Default.Block,
                    settings.popupBlock, "Block pop-ups", settings::changePopupBlock)
                AnimatedVisibility(visible = settings.failed, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                    Text("Could not apply that setting. Your previous setting was restored.", color = com.example.ui.theme.HiErrorRed)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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
    val background by animateColorAsState(if (checked) palette.primary.copy(alpha = .10f) else palette.surfaceElevated.copy(alpha = .45f), label = "gecko-setting")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(background)
            .clickable { onCheckedChange(!checked) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (checked) palette.primary else palette.textSecondary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = palette.textPrimary)
            Text(description, color = palette.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { this.contentDescription = contentDescription })
    }
}
