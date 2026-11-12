package com.example.browser.gecko

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.ui.screens.GeckoBrowserArguments
import com.example.ui.screens.OrbitBrowserFilterEngine
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared browser features backed by Gecko, without reintroducing WebView settings. */
@Composable
internal fun GeckoSettingsPanel(args: GeckoBrowserArguments, services: GeckoBrowserServices,
    store: OrbitBrowserStore, onAccount: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val palette = LocalHiPalette.current
    val scope = rememberCoroutineScope()
    val current = services.coordinator.activeSnapshot
    var updating by remember { mutableStateOf(false) }
    var updateJob by remember { mutableStateOf<Job?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = palette.background, contentColor = palette.textPrimary) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Back") }
                Text("Browser settings", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GeckoSettingsGroup("Appearance") {
                    GeckoAppearanceControls(services.settings)
                }
                GeckoSettingsGroup("Privacy and content") {
                    GeckoFeatureSwitch("Block pop-ups", services.settings.popupBlock, change = services.settings::changePopupBlock,
                        description = "Allow user-initiated windows; block unsolicited pop-ups.")
                    GeckoProtectionControls(services.settings)
                }
                Text("Changes apply on supported pages after reload. Saved site rules still apply; security checks and server-enforced waits are not bypassed.", style = MaterialTheme.typography.bodySmall)
                if (BuildConfig.PLAY_STORE_BUILD) Text("Page and YouTube helpers are unavailable in the Play build.", style = MaterialTheme.typography.bodySmall)
                if (services.settings.failed) Text("Could not save that setting. Your previous setting was restored.", color = com.example.ui.theme.HiErrorRed)
                if (services.settings.contentNeedsReload) {
                    TextButton(enabled = current.url.isNotBlank(), onClick = { services.settings.markContentApplied(); services.coordinator.reload() }) { Text("Reload current page") }
                    Text("Reloading may discard unsent page input.", style = MaterialTheme.typography.bodySmall)
                }
                GeckoSettingsGroup("Videos") {
                    GeckoFeatureSwitch("Detect website videos", services.settings.videoHandoff, change = services.settings::changeVideoHandoff,
                        description = "Use the browser menu to play supported videos. Protected or blob media stays in Gecko.")
                }
                GeckoSettingsGroup("This tab") {
                    GeckoFeatureSwitch("Desktop site", services.coordinator.desktopMode(), current.url.isNotBlank(), {
                        services.coordinator.setDesktop(it); services.coordinator.reload()
                    }, "Changing this setting reloads the tab; unsent input may be lost.")
                }
                Text("Filter data", style = MaterialTheme.typography.titleMedium)
                Text("Ad/tracker lists can be refreshed independently. Bundled page-helper code updates with the app.", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !updating, onClick = {
                    if (!updating) {
                        updating = true; feedback = "Checking filter data…"
                        updateJob = scope.launch {
                            val job = coroutineContext[Job]
                            try {
                                val changed = withContext(Dispatchers.IO) {
                                    if (BuildConfig.PLAY_STORE_BUILD) OrbitBrowserFilterEngine.refreshPlayFilters(context)
                                    else OrbitBrowserFilterEngine.update(context) { job?.isActive != true }
                                }
                                feedback = if (changed) "Filter data loaded. Existing pages are not reloaded automatically."
                                    else "Check finished. Previously loaded filters remain active."
                            } catch (e: CancellationException) { feedback = "Filter check cancelled."; throw e
                            } catch (_: Exception) { feedback = "Could not check filters. Previously loaded filters remain active."
                            } finally { updating = false; updateJob = null }
                        }
                    }
                }) { Text(if (updating) "Checking filters…" else "Check filter data") }
                if (updating) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { updateJob?.cancel() }) { Text("Cancel filter check") }
                }
                feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Browsing data", style = MaterialTheme.typography.titleMedium)
                Text("Use Clear browsing data in the browser menu to choose history, cookies and cache. Clearing still requires confirmation.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    }
}

@Composable
internal fun GeckoAppearanceControls(settings: GeckoBrowserSettings) {
    val palette = LocalHiPalette.current
    var preview by remember(settings, settings.zoom) { mutableIntStateOf(settings.zoom) }
    GeckoFeatureSwitch("Dark web pages", settings.dark, change = { settings.setAppearance(it, settings.zoom) },
        description = "Use a dark color scheme on compatible sites.")
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text("Webpage text size · $preview%", color = palette.textPrimary)
        Text("Applies to supported pages after reload; unsent input may be lost.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        Slider(value = preview.toFloat(), valueRange = 80f..200f, steps = 11,
            onValueChange = { preview = ((it / 10f).roundToInt() * 10).coerceIn(80, 200) },
            onValueChangeFinished = { settings.setAppearance(settings.dark, preview); preview = settings.zoom },
            modifier = Modifier.semantics { contentDescription = "Webpage text size" })
        TextButton(enabled = settings.zoom != 100 || preview != 100,
            onClick = { settings.setAppearance(settings.dark, 100); preview = settings.zoom }) { Text("Reset to 100%") }
    }
}

@Composable
internal fun GeckoProtectionControls(settings: GeckoBrowserSettings) {
    GeckoFeatureSwitch("Ad and tracker blocking", settings.adBlock, change = settings::changeAdBlock,
        description = "Block common ad and tracker requests.")
    GeckoFeatureSwitch("Skip supported wait pages", settings.pageSkipper && !BuildConfig.PLAY_STORE_BUILD, !BuildConfig.PLAY_STORE_BUILD, settings::changePageSkipper,
        "Try to continue through supported pages.")
    GeckoFeatureSwitch("Skip supported YouTube ads", settings.youtubeAdSkip && !BuildConfig.PLAY_STORE_BUILD, !BuildConfig.PLAY_STORE_BUILD, settings::changeYoutubeAdSkip,
        "Mute, fast-forward or skip supported ads.")
}

@Composable
private fun GeckoSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalHiPalette.current
    Text(title.uppercase(), color = palette.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface)
        .padding(horizontal = 16.dp, vertical = 6.dp), content = content)
}

@Composable
private fun GeckoFeatureSwitch(title: String, checked: Boolean, enabled: Boolean = true,
    change: (Boolean) -> Unit, description: String = "") {
    val palette = LocalHiPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotEmpty()) Text(description, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = change, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title })
    }
}
