package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.JellyfinMediaSourceClient
import com.example.ui.feedback.SourceSetupDraft
import com.example.viewmodel.DiscoverViewModel
import kotlinx.coroutines.*

/** Official device-code flow. Secret/token remain session-only; the web tab is not scraped. */
@Composable
internal fun JellyfinQuickConnectDialog(draft: SourceSetupDraft, model: DiscoverViewModel,
    onDismiss: () -> Unit, onPassword: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by draft.url
    var code by remember { mutableStateOf<String?>(null) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var insecure by remember(url) { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val cancel = { job?.cancel(); onDismiss() }
    AlertDialog(onDismissRequest = cancel, title = { Text("Connect Jellyfin") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Optional: only use this if you already have access to a Jellyfin media server. It does not provide movies or require a Hi Player Google account.")
            OutlinedTextField(url, { url = it }, label = { Text("Server address") }, placeholder = { Text("https://your-server.example") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (url.trim().startsWith("http://", true)) {
                Text("HTTP is not encrypted. Prefer HTTPS; only continue on a network and server you trust.")
                Row { Checkbox(insecure, { insecure = it }, enabled = !busy); Text("Allow this unencrypted connection", Modifier.weight(1f)) }
            }
            code?.let { value ->
                Text(value, style = MaterialTheme.typography.headlineLarge)
                Text("In an already signed-in Jellyfin app or web interface, open Settings → Quick Connect and approve this code. Hi Player checks for approval every five seconds. Approval expires after about two minutes; a slow network request may take a little longer to stop.")
                TextButton(onClick = {
                    val target = webUrl ?: return@TextButton
                    try { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(target)) }
                    catch (_: android.content.ActivityNotFoundException) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                            .onFailure { message = "No supported browser is available. Approve the code from another Jellyfin app." }
                    }
                }) { Text("Open server web sign-in") }
            }
            message?.let { Text(it) }
            TextButton(onClick = { job?.cancel(); onPassword() }) { Text("Use username and password instead") }
        }
    }, confirmButton = {
        Button(enabled = !busy && url.isNotBlank() && (!url.trim().startsWith("http://", true) || insecure), onClick = {
            if (busy) return@Button
            busy = true; message = "Requesting a sign-in code…"; code = null; webUrl = null
            val server = url
            val generation = model.beginJellyfinAuthorization()
            job = scope.launch {
                try {
                    val connection = withTimeout(120_000) {
                        val session = JellyfinMediaSourceClient.beginQuickConnect(server)
                        ensureActive(); code = session.code; webUrl = session.baseUrl + "/web/"; message = "Waiting for your approval…"
                        var authorized: com.example.model.JellyfinConnection? = null
                        while (authorized == null) {
                            delay(5_000)
                            authorized = JellyfinMediaSourceClient.pollQuickConnect(session)
                        }
                        requireNotNull(authorized)
                    }
                    ensureActive(); model.completeJellyfinAuthorization(generation, connection)
                    ensureActive(); onConnected()
                } catch (_: TimeoutCancellationException) {
                    message = "Approval timed out. Get a new code or use username and password."
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { message = "Couldn't complete Quick Connect. Check the direct server address. If Quick Connect is disabled or unsupported, use username and password."
                } finally { busy = false; code = null; webUrl = null }
            }
        }) { Text(if (busy) "Waiting…" else "Get sign-in code") }
    }, dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } })
}
