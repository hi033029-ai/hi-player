package com.example.browser.gecko

import android.content.Context
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.screens.OrbitBrowserStore
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance and pop-ups") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dark web pages")
                    Switch(checked = settings.dark, onCheckedChange = { settings.setAppearance(it, settings.zoom) }, modifier = Modifier.semantics { contentDescription = "Dark web pages" })
                }
                Text("Webpage text · $preview%")
                Slider(value = preview.toFloat(), valueRange = 80f..200f, steps = 11,
                    onValueChange = { preview = ((it / 10f).roundToInt() * 10).coerceIn(80, 200) },
                    onValueChangeFinished = { settings.setAppearance(settings.dark, preview); preview = settings.zoom },
                    modifier = Modifier.semantics { contentDescription = "Webpage text size" })
                TextButton(onClick = { settings.setAppearance(settings.dark, 100); preview = settings.zoom }) { Text("Reset webpage text") }
                Text("Changes apply to all tabs. Reload pages to apply text size; unsent input may be lost when reloading.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Block pop-ups", Modifier.padding(end = 12.dp).weight(1f))
                    Switch(checked = settings.popupBlock, onCheckedChange = settings::changePopupBlock, modifier = Modifier.semantics { contentDescription = "Block pop-ups" })
                }
                if (settings.failed) Text("Could not apply that setting. Please try again.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
