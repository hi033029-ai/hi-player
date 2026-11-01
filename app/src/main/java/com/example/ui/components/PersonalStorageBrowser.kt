package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.connections.*
import com.example.model.VideoItem
import com.example.ui.theme.LocalHiPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonalStorageBrowser(kind: String, onDismiss: () -> Unit, onPlay: (VideoItem) -> Unit,
    model: StorageBrowserViewModel = viewModel()) {
    val context = LocalContext.current; val palette = LocalHiPalette.current
    val clouds by PersonalCloud.status.collectAsState(); val smb by SmbStorage.status.collectAsState()
    val provider = CloudProvider.entries.firstOrNull { it.key == kind }
    val status = provider?.let { clouds.getValue(it) }
    val session = if (kind == "smb") smb.id else status?.sessionId
    val title = provider?.label ?: "Network folders (SMB)"
    val state by model.state.collectAsState()
    var setupSmb by remember { mutableStateOf(false) }; var disconnect by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, session) { model.open(kind, session, title); playbackError = null }
    DisposableEffect(kind) { onDispose { model.clear(); provider?.let { if (PersonalCloud.status.value.getValue(it).busy) PersonalCloud.cancel(it) } } }
    Dialog(onDismissRequest = { if (state.path.size > 1) model.back() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(state.path.size > 1) { model.back() }
        Surface(Modifier.fillMaxSize(), color = palette.background, contentColor = palette.textPrimary) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (state.path.size > 1) model.back() else onDismiss() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text(state.path.lastOrNull()?.name ?: title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                LazyColumn(Modifier.weight(1f), state = remember(state.kind, state.session, state.path.lastOrNull()?.id) { LazyListState() }, contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        if (session == null) {
                            Text(if (kind == "smb") "Browse a shared folder on your own NAS or computer. Use your server's address and share name. No SMB1 support."
                                else "Allow read-only access to your ${provider?.label} files in the official browser sign-in. Hi Player cannot edit or delete them. This connection is separate from Google sign-in.")
                            Text("Connections are kept for this app session only. Reconnect after restarting the app or when authorization expires.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        if (status?.account?.isNotBlank() == true) Text(status.account)
                        if (provider != null && provider.clientId.isBlank()) Text("Setup required: the developer must register ${provider.label} and configure its public OAuth app ID. Sign-in is not enabled in this build.", modifier = Modifier.padding(vertical = 8.dp))
                        FlowRow {
                            TextButton(enabled = status?.busy != true && (provider == null || provider.clientId.isNotBlank()), onClick = { if (provider == null) setupSmb = true else PersonalCloud.connect(context, provider) }) { Text(if (session == null) "Connect" else "Reconnect") }
                            if (status?.busy == true) TextButton(onClick = { PersonalCloud.cancel(checkNotNull(provider)) }) { Text("Cancel sign-in") }
                            if (session != null) TextButton(onClick = { disconnect = true }) { Text("Disconnect") }
                        }
                        status?.message?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
                        if (session != null) {
                            FlowRow {
                                TextButton(enabled = !state.busy, onClick = { model.load() }) { Text("Refresh") }
                                if (state.busy) TextButton(onClick = model::stop) { Text("Stop loading") }
                            }
                            state.message?.let { Text(it); TextButton(enabled = !state.busy, onClick = { model.load() }) { Text("Try again") } }
                            playbackError?.let { Text(it) }; state.note?.let { Text(it) }
                        }
                    }
                    if (session != null) {
                        if (state.kind != kind || state.session != session || state.busy && state.files.isEmpty()) item {
                            ContentSkeleton("Loading files…", Modifier.fillMaxWidth().height(320.dp), SkeletonLayout.FILES)
                        } else {
                            if (state.files.isEmpty() && !state.busy && state.message == null) item { Text("No files in this folder.", Modifier.padding(vertical = 24.dp)) }
                            items(state.files, key = { it.id }) { file ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = !state.busy && status?.busy != true && (file.folder || file.playable)) {
                                    if (file.folder) model.enter(file) else {
                                        val uri = runCatching { model.media(file) }.getOrNull()
                                        if (uri == null) playbackError = "This connection expired or changed. Reconnect and try again."
                                        else { onDismiss(); onPlay(VideoItem(uri = uri, title = file.name, mimeType = file.mime, sizeBytes = file.size, folderName = title, path = "personal-storage:${file.id}")) }
                                    }
                                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (file.folder) Icons.Default.Folder else if (file.mime.startsWith("audio/")) Icons.Default.MusicNote else Icons.Default.Movie, null, tint = palette.primary)
                                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                                        Text(file.name); Text(if (file.folder) "Folder" else if (!file.playable) "Unsupported file type" else "Play in Hi Player", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            if (state.next != null) item { TextButton(enabled = !state.busy, onClick = { model.load(more = true) }) { Text("Load more") } }
                        }
                    }
                }
            }
        }
    }
    if (setupSmb) SmbSetupDialog(onDismiss = { setupSmb = false }, onConnected = { setupSmb = false })
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect $title?") },
        text = { Text("Clears this device's session and stops its playback access. Your files and provider website sign-in are unchanged.") },
        confirmButton = { TextButton(onClick = { disconnect = false; if (provider == null) SmbStorage.disconnect() else PersonalCloud.disconnect(provider); model.clear() }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}

@Composable
private fun SmbSetupDialog(onDismiss: () -> Unit, onConnected: () -> Unit, model: SmbSetupViewModel = viewModel()) {
    var host by model.host; var share by model.share; var username by model.username; var password by model.password
    var domain by model.domain; var encrypted by model.encrypted; val busy by model.busy; val message by model.message
    val cancel = { model.discard(); onDismiss() }
    DisposableEffect(Unit) { onDispose { model.stop() } }
    AlertDialog(onDismissRequest = cancel, title = { Text("Connect network folder") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Use a read-only account on a NAS or shared computer. The share must already exist; Hi Player does not configure your server.")
            OutlinedTextField(host, { host = it }, label = { Text("Server hostname or IP") }, placeholder = { Text("192.168.1.10") }, enabled = !busy, singleLine = true)
            OutlinedTextField(share, { share = it }, label = { Text("Share name") }, placeholder = { Text("Media") }, enabled = !busy, singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, enabled = !busy, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
            OutlinedTextField(domain, { domain = it }, label = { Text("Domain (optional)") }, enabled = !busy, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(encrypted, { encrypted = it }, enabled = !busy); Text("Require SMB3 encryption", Modifier.weight(1f)) }
            if (!encrypted) Text("Unencrypted SMB2/3: only use on a trusted local network or VPN. Signing remains required; SMB1 is never enabled.")
            message?.let { Text(it) }
        }
    }, confirmButton = { Button(enabled = !busy && host.isNotBlank() && share.isNotBlank() && username.isNotBlank(),
        onClick = { model.connect { model.discard(); onConnected() } }) { Text(if (busy) "Connecting…" else "Connect") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } })
}
