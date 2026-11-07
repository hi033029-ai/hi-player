package com.example.browser.gecko

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.screens.GeckoBrowserArguments
import com.example.ui.screens.OrbitBrowserStore

/** Real controls only: protected/blob media stays in Gecko; website cookies are not exported. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.GeckoPageActions(args: GeckoBrowserArguments, services: GeckoBrowserServices, store: OrbitBrowserStore) {
    val current = services.coordinator.activeSnapshot
    val tab = services.coordinator.controller.activeSession() as? GeckoManagedTab
    var options by remember { mutableStateOf(false) }
    var reader by remember { mutableStateOf(false) }
    var confirmMedia by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var blocking by remember { mutableStateOf(store.adBlock) }
    var helper by remember { mutableStateOf(store.pageSkipper) }
    LaunchedEffect(current.id, current.url) { reader = false; confirmMedia = false }
    FilledTonalButton(onClick = { options = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
        Text(if (current.url.isBlank()) "Browser options" else "Page actions")
    }
    if (options) ModalBottomSheet(onDismissRequest = { options = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Browser controls", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ad and tracker blocking", Modifier.weight(1f))
                Switch(checked = blocking, onCheckedChange = { blocking = it; store.adBlock = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compatible page helpers", Modifier.weight(1f))
                Switch(checked = helper && !BuildConfig.PLAY_STORE_BUILD, enabled = !BuildConfig.PLAY_STORE_BUILD,
                    onCheckedChange = { helper = it; store.pageSkipper = it })
            }
            Text("Protection and helper changes apply when you reload. Helpers leave sign-in, checkout and security challenges alone.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { options = false; services.coordinator.reload() }, enabled = current.url.isNotBlank()) { Text("Reload page") }
            TextButton(enabled = current.mediaUrl != null, onClick = { options = false; confirmMedia = true }) { Text("Play detected media in Hi Player") }
            TextButton(enabled = current.url.isNotBlank() && args.onEnterPictureInPicture != null, onClick = {
                options = false
                if (args.onEnterPictureInPicture?.invoke() != true) notice = "Picture in picture is unavailable on this device."
            }) { Text("Picture in picture") }
            TextButton(enabled = current.url.isNotBlank() && tab != null, onClick = {
                options = false
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
