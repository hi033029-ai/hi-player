package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.DiscoverViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun SavedMediaSources(model: DiscoverViewModel, addJellyfin: () -> Unit, addWebDav: () -> Unit) {
    val profiles by model.savedSourceProfiles.collectAsState()
    val sources by model.sourceSettings.collectAsState()
    val scope = rememberCoroutineScope()
    // Labels may contain personal information; keep this draft session-only.
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    fun action(success: String, operation: suspend () -> Unit) {
        if (busy) return
        busy = true; message = "Working…"
        job = scope.launch {
            try { operation(); message = success
            } catch (e: CancellationException) { message = "Stopped. Check the active connection; a completed switch is kept."; throw e
            } catch (_: Exception) { message = "Couldn't update the connection. Check server access and credentials, then try again. Up to 12 connections can be saved."
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved media connections", style = MaterialTheme.typography.titleMedium)
        Text("Save the currently connected server, then add another. Switching verifies access first. One Jellyfin and one WebDAV connection can be active at a time.")
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Connection name") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = !busy && name.isNotBlank() && sources.jellyfin?.isConfigured == true && profiles.size < 12,
            onClick = { val label = name; action("Jellyfin connection saved") { model.saveSourceProfile(label, "jellyfin") } }) { Text("Save connected Jellyfin") }
        TextButton(enabled = !busy && name.isNotBlank() && sources.webDav?.isConfigured == true && profiles.size < 12,
            onClick = { val label = name; action("WebDAV connection saved") { model.saveSourceProfile(label, "webdav") } }) { Text("Save connected WebDAV") }
        TextButton(enabled = !busy, onClick = addJellyfin) { Text("Connect another Jellyfin server") }
        TextButton(enabled = !busy, onClick = addWebDav) { Text("Connect another WebDAV folder") }
        profiles.forEach { profile ->
            Text("${profile.name} · ${if (profile.kind == "jellyfin") "Jellyfin" else "WebDAV"}")
            Row {
                TextButton(enabled = !busy, onClick = { action("Connection verified and activated") { model.activateSourceProfile(profile.id) } }) { Text("Connect") }
                TextButton(enabled = !busy, onClick = { deleteId = profile.id }) { Text("Forget") }
            }
        }
        if (busy) TextButton(onClick = { job?.cancel() }) { Text("Stop") }
        message?.let { Text(it) }
        Text("Connection details stay encrypted on this device. Disconnecting an active source does not erase saved copies; use Forget to remove a saved connection. This does not sign websites or Google accounts in.", style = MaterialTheme.typography.bodySmall)
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Forget saved connection?") }, text = { Text("Removes its saved credentials. The currently active connection remains until disconnected separately.") },
        confirmButton = { TextButton(onClick = { val id = deleteId!!; deleteId = null; action("Saved connection forgotten") { model.deleteSourceProfile(id) } }) { Text("Forget") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
}
