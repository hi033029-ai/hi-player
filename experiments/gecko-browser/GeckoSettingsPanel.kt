package com.example.browser.gecko

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
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
import com.example.ui.screens.BrowserAdvancedTools
import com.example.ui.screens.GeckoBrowserArguments
import com.example.ui.screens.OrbitBrowserFilterEngine
import com.example.ui.screens.OrbitBrowserStore
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Shared browser features backed by Gecko, without reintroducing WebView settings. */
@Composable
internal fun GeckoSettingsPanel(args: GeckoBrowserArguments, services: GeckoBrowserServices,
    store: OrbitBrowserStore, onAccount: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val palette = LocalHiPalette.current
    val scope = rememberCoroutineScope()
    val current = services.coordinator.activeSnapshot
    val host = remember(current.url) { Uri.parse(current.url).host }
    var appearance by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(store.adBlock) }
    var detection by remember { mutableStateOf(store.videoHandoff) }
    var textSize by remember(args.appTextSizeSp) { mutableIntStateOf(args.appTextSizeSp.coerceIn(12, 24)) }
    var webpageZoom by remember(services.settings) { mutableIntStateOf(services.settings.zoom) }
    var updating by remember { mutableStateOf(false) }
    var updateJob by remember { mutableStateOf<Job?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(palette.background).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Back") }
                Text("Browser settings", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GeckoSettingsGroup("Account", "Website sessions stay separate from the Hi Player account.") {
                    TextButton(onClick = onAccount) { Text("Hi Player account and cloud media") }
                }
                GeckoSettingsGroup("Appearance", "Webpage settings apply independently from app-wide text size.") {
                    Text("Webpage text · ${webpageZoom}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = webpageZoom.toFloat(), valueRange = 80f..200f, steps = 11,
                        onValueChange = { webpageZoom = ((it / 10f).roundToInt() * 10).coerceIn(80, 200) },
                        onValueChangeFinished = {
                            val previous = services.settings.zoom
                            services.settings.setAppearance(services.settings.dark, webpageZoom)
                            webpageZoom = services.settings.zoom
                            if (services.settings.zoom != previous && current.url.isNotBlank()) {
                                services.coordinator.reload()
                                feedback = "Webpage text updated. This tab reloaded; unsent form input may have been lost."
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = "Webpage text size" })
                    Text("Changing this reloads the current page. Unsent form input may be lost.", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = webpageZoom != 100, onClick = {
                        services.settings.setAppearance(services.settings.dark, 100)
                        webpageZoom = services.settings.zoom
                        if (current.url.isNotBlank()) services.coordinator.reload()
                    }) { Text("Reset webpage text to 100%") }
                    TextButton(onClick = { appearance = true }) { Text("More appearance and privacy settings") }
                    HorizontalDivider()
                    Text("Browser controls · ${textSize}sp", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = textSize.toFloat(), valueRange = 12f..24f, steps = 11,
                        onValueChange = { textSize = it.roundToInt() },
                        onValueChangeFinished = { args.onAppTextSizeChange(textSize) },
                        modifier = Modifier.semantics { contentDescription = "App text size" })
                    TextButton(enabled = textSize != 14, onClick = { textSize = 14; args.onAppTextSizeChange(14) }) { Text("Reset app text to 14sp") }
                }
                GeckoSettingsGroup("Privacy and content", "Ad blocking applies on page loads; it does not bypass security challenges or server-enforced waits.") {
                    GeckoFeatureSwitch("Ad and tracker blocking", blocking) { blocking = it; store.adBlock = it }
                }
                GeckoSettingsGroup("Advanced site tools", "Rules are stored by hostname. Explicit blocks win over allows.") {
                    BrowserAdvancedTools(host) { feedback = "Site rules saved. Reload to apply page helpers; unsent page input is not reloaded automatically." }
                }
                GeckoSettingsGroup("Videos", "Website cookies and protected or blob media stay in Gecko.") {
                    GeckoFeatureSwitch("Detect website videos", detection) { detection = it; store.videoHandoff = it }
                    Text("Use Page tools to open detected media in Hi Player.", style = MaterialTheme.typography.bodySmall)
                }
                GeckoSettingsGroup("This tab", "Changes apply to the current tab.") {
                    GeckoFeatureSwitch("Desktop site", services.coordinator.desktopMode(), current.url.isNotBlank()) {
                        services.coordinator.setDesktop(it); services.coordinator.reload()
                    }
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
    if (appearance) GeckoAppearanceDialog(
        services.settings,
        canReloadCurrentPage = current.url.isNotBlank(),
        onReloadCurrentPage = { services.coordinator.reload() },
    ) { appearance = false }
}

@Composable
private fun GeckoFeatureSwitch(title: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

@Composable
private fun GeckoSettingsGroup(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalHiPalette.current
    Column(
        Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        content()
    }
}
