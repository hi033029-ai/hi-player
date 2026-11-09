package com.example.browser.gecko

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.screens.BrowserAdvancedRules
import com.example.ui.screens.BrowserSiteTools
import com.example.ui.screens.GeckoBrowserArguments
import com.example.ui.screens.OrbitBrowserStore

/** Real controls only: protected/blob media stays in Gecko; website cookies are not exported. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeckoPageToolsSheet(
    args: GeckoBrowserArguments,
    services: GeckoBrowserServices,
    store: OrbitBrowserStore,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val current = services.coordinator.activeSnapshot
    val tab = services.coordinator.controller.activeSession() as? GeckoManagedTab
    val context = LocalContext.current
    val pageHost = remember(current.url) { BrowserAdvancedRules.host(Uri.parse(current.url).host.orEmpty()) }
    var reader by remember { mutableStateOf(false) }
    var confirmMedia by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var blocking by remember { mutableStateOf(store.adBlock) }
    var helper by remember { mutableStateOf(store.pageSkipper) }
    var youtubeAds by remember { mutableStateOf(store.youtubeAdSkip) }
    var sitePaused by remember(pageHost) { mutableStateOf(BrowserAdvancedRules.paused(pageHost)) }
    LaunchedEffect(current.id, current.url, store.adBlock, store.pageSkipper, store.youtubeAdSkip) {
        reader = false
        confirmMedia = false
        blocking = store.adBlock
        helper = store.pageSkipper
        youtubeAds = store.youtubeAdSkip
        sitePaused = BrowserAdvancedRules.paused(pageHost)
    }
    if (visible) ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Page tools", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ad and tracker blocking", Modifier.weight(1f))
                Switch(checked = blocking, onCheckedChange = { blocking = it; store.adBlock = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pause protection on this site")
                    Text("Turn off ad and tracker filtering for ${pageHost ?: "this page"}.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = sitePaused, enabled = pageHost != null, onCheckedChange = { paused ->
                    val host = pageHost
                    if (host != null) {
                        val rules = BrowserAdvancedRules.snapshot()
                        val sites = rules.sites.toMutableMap()
                        val previous = sites[host] ?: BrowserSiteTools()
                        val next = previous.copy(pauseBlocking = paused)
                        if (next == BrowserSiteTools()) sites.remove(host) else sites[host] = next
                        runCatching { BrowserAdvancedRules.save(context, rules.copy(sites = sites)) }
                            .onSuccess { sitePaused = paused }
                            .onFailure { notice = "Could not save this site's protection setting." }
                    }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compatible page helpers", Modifier.weight(1f))
                Switch(checked = helper && !BuildConfig.PLAY_STORE_BUILD, enabled = !BuildConfig.PLAY_STORE_BUILD,
                    onCheckedChange = { helper = it; store.pageSkipper = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip supported YouTube ads")
                    Text("Hides supported ad placements; creator sponsorship segments are not detected.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = youtubeAds && !BuildConfig.PLAY_STORE_BUILD, enabled = !BuildConfig.PLAY_STORE_BUILD,
                    onCheckedChange = { youtubeAds = it; store.youtubeAdSkip = it })
            }
            Text("Protection and helper changes apply when you reload. Helpers leave sign-in, checkout and security challenges alone.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onDismiss(); services.coordinator.reload() }, enabled = current.url.isNotBlank()) { Text("Reload page") }
            TextButton(enabled = current.mediaUrl != null, onClick = { onDismiss(); confirmMedia = true }) { Text("Play detected media in Hi Player") }
            TextButton(enabled = current.url.isNotBlank() && args.onEnterPictureInPicture != null, onClick = {
                onDismiss()
                if (args.onEnterPictureInPicture?.invoke() != true) notice = "Picture in picture is unavailable on this device."
            }) { Text("Picture in picture") }
            TextButton(enabled = current.url.isNotBlank() && tab != null, onClick = {
                onDismiss()
                tab?.engine?.clearReader()
                if (tab != null && GeckoFeatureBridge.requestReader(tab.engine.session)) reader = true
                else notice = "This page does not offer readable text."
            }) { Text("Reader mode") }
            Text("Website media pauses when the browser is inactive. Use picture in picture or Hi Player for background playback. Protected and blob media remain in the website.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmMedia && current.mediaUrl != null) AlertDialog(
        onDismissRequest = { confirmMedia = false }, title = { Text("Open media in Hi Player?") },
        text = { Text("Source: ${android.net.Uri.parse(current.mediaUrl).host.orEmpty()}\nOnly the media address, type and title are handed off. Website cookies and DRM keys stay in Gecko.") },
        confirmButton = { TextButton(onClick = {
            confirmMedia = false
            val url = current.mediaUrl
            if (url != null) {
                if (args.onPlayBrowserVideo != null) args.onPlayBrowserVideo.invoke(url, current.mediaMime, current.mediaTitle)
                else args.onPlayDirectMedia(url)
            }
        }) { Text("Play") } },
        dismissButton = { TextButton(onClick = { confirmMedia = false }) { Text("Stay in browser") } },
    )
    if (reader) AlertDialog(
        onDismissRequest = { reader = false }, title = { Text(current.title) },
        text = {
            if (current.readerText == null) LinearProgressIndicator()
            else Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                Text(current.readerText?.takeIf { it.isNotBlank() } ?: "No readable text on this page.")
            }
        }, confirmButton = { TextButton(onClick = { reader = false }) { Text("Close") } },
    )
    notice?.let { message -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("Browser") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }) }
}
