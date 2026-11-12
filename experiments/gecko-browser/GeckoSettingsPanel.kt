package com.example.browser.gecko

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
    var appearance by remember { mutableStateOf(false) }
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
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { appearance = true }) { Text("Webpage appearance, text and pop-ups") }
                Text("Advanced protection", style = MaterialTheme.typography.titleMedium)
                GeckoProtectionControls(services.settings)
                Text("Applies to supported pages after reload. Existing saved site rules still apply. Security checks and server-enforced waits are not bypassed.", style = MaterialTheme.typography.bodySmall)
                if (BuildConfig.PLAY_STORE_BUILD) Text("Page and YouTube helpers are unavailable in the Play build.", style = MaterialTheme.typography.bodySmall)
                if (services.settings.failed) Text("Could not save that setting. Your previous setting was restored.", color = com.example.ui.theme.HiErrorRed)
                if (services.settings.contentNeedsReload) {
                    TextButton(enabled = current.url.isNotBlank(), onClick = { services.settings.markContentApplied(); services.coordinator.reload() }) { Text("Reload current page") }
                    Text("Reloading may discard unsent page input.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Videos", style = MaterialTheme.typography.titleMedium)
                GeckoFeatureSwitch("Detect website videos", services.settings.videoHandoff, change = services.settings::changeVideoHandoff)
                Text("Use Play detected video in the browser menu when a supported video is found. Website cookies and protected/blob media stay in Gecko.", style = MaterialTheme.typography.bodySmall)
                Text("This tab", style = MaterialTheme.typography.titleMedium)
                GeckoFeatureSwitch("Desktop site", services.coordinator.desktopMode(), current.url.isNotBlank()) {
                    services.coordinator.setDesktop(it); services.coordinator.reload()
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
    if (appearance) GeckoAppearanceDialog(services.settings) { appearance = false }
}

@Composable
internal fun GeckoProtectionControls(settings: GeckoBrowserSettings) {
    GeckoFeatureSwitch("Ad and tracker blocking", settings.adBlock, change = settings::changeAdBlock)
    GeckoFeatureSwitch("Skip supported wait pages", settings.pageSkipper && !BuildConfig.PLAY_STORE_BUILD, !BuildConfig.PLAY_STORE_BUILD, settings::changePageSkipper)
    GeckoFeatureSwitch("Skip supported YouTube ads", settings.youtubeAdSkip && !BuildConfig.PLAY_STORE_BUILD, !BuildConfig.PLAY_STORE_BUILD, settings::changeYoutubeAdSkip)
}

@Composable
private fun GeckoFeatureSwitch(title: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title })
    }
}
