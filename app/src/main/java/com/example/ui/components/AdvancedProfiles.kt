package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.PlayerPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Named snapshots of actual settings, not a separate or simulated playback engine. */
@Composable
fun AdvancedProfiles() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { PlayerPreferencesRepository(context) }
    val profiles by repository.profilesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf("playback") }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    fun action(success: String, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = "Updating profiles…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) { operation() }
                message = success
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message = "Couldn't update profiles. Try again; up to 24 profiles can be saved."
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved profiles", style = MaterialTheme.typography.titleMedium)
        Text("Save current video playback, video equalizer, subtitle and HDR-enhancement settings, or a separate theme palette. HDR still requires compatible content and hardware.")
        Row {
            FilterChip(selected = kind == "playback", onClick = { kind = "playback" }, label = { Text("Playback") }, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = kind == "theme", onClick = { kind = "theme" }, label = { Text("Theme") }, enabled = !busy)
        }
        OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Profile name") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && name.isNotBlank() && profiles.size < 24, onClick = {
            val savedName = name; val savedKind = kind
            action("Profile saved") { repository.saveProfile(savedName, savedKind) }
        }) { Text("Save current settings") }
        profiles.filter { it.kind == kind }.forEach { profile ->
            Column(Modifier.fillMaxWidth()) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(enabled = !busy, onClick = { action("Profile applied") { repository.applyProfile(profile.id) } }) { Text("Apply") }
                    TextButton(enabled = !busy, onClick = { deleteId = profile.id }) { Text("Delete") }
                }
            }
        }
        message?.let { Text(it) }
        Text("Profiles stay on this device. Accounts, credentials, volume, brightness and permission consent are not included.", style = MaterialTheme.typography.bodySmall)
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete saved profile?") },
        text = { Text("Your current settings won't change.") },
        confirmButton = { TextButton(onClick = { val id = deleteId!!; deleteId = null; action("Profile deleted") { repository.deleteProfile(id) } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
}
