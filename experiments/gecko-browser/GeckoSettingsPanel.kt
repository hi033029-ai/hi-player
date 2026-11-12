package com.example.browser.gecko

import android.net.Uri
import androidx.compose.foundation.background
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
    var textSize by remember(args.appTextSizeSp) { mutableIntStateOf(args.appTextSizeSp.coerceIn(12, 24)) }
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
                Text("Account", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAccount) { Text("Hi Player account and cloud media") }
                Text("Website sessions stay separate from the Hi Player account.", style = MaterialTheme.typography.bodySmall)
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { appearance = true }) { Text("Webpage appearance, text and pop-ups") }
                Text("App text · ${textSize}sp")
                Slider(value = textSize.toFloat(), valueRange = 12f..24f, steps = 11,
                    onValueChange = { textSize = it.roundToInt() },
                    onValueChangeFinished = { args.onAppTextSizeChange(textSize) },
                    modifier = Modifier.semantics { contentDescription = "App text size" })
                TextButton(enabled = textSize != 14, onClick = { textSize = 14; args.onAppTextSizeChange(14) }) { Text("Reset app text to 14sp") }
                Text("Privacy and content", style = MaterialTheme.typography.titleMedium)
                GeckoFeatureSwitch("Ad and tracker blocking", services.settings.adBlock, change = services.settings::changeAdBlock)
                GeckoFeatureSwitch("Skip supported wait pages", services.settings.pageSkipper, !BuildConfig.PLAY_STORE_BUILD, services.settings::changePageSkipper)
                GeckoFeatureSwitch("Skip supported YouTube ads", services.settings.youtubeAdSkip, !BuildConfig.PLAY_STORE_BUILD, services.settings::changeYoutubeAdSkip)
                Text("Helpers apply on the next page load. They do not bypass sign-in, consent, CAPTCHA, checkout or server-enforced waits.", style = MaterialTheme.typography.bodySmall)
                if (services.settings.failed) Text("Could not save that setting. Your previous setting was restored.", color = com.example.ui.theme.HiErrorRed)
                BrowserAdvancedTools(host) { feedback = "Site rules saved. Reload to apply page helpers; unsent page input is not reloaded automatically." }
                Text("Videos", style = MaterialTheme.typography.titleMedium)
                GeckoFeatureSwitch("Detect website videos", services.settings.videoHandoff, change = services.settings::changeVideoHandoff)
                Text("Use Page tools to open detected media in Hi Player. Website cookies and protected/blob media stay in Gecko.", style = MaterialTheme.typography.bodySmall)
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
private fun GeckoFeatureSwitch(title: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}
