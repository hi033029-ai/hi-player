package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.DiscoverViewModel

/** Optional user-owned network libraries, separate from the Hi Player account. */
@Composable
fun DiscoverSourceSettings(
    discoverViewModel: DiscoverViewModel,
    modifier: Modifier = Modifier,
    drafts: com.example.ui.feedback.SessionDraftsViewModel = viewModel(),
    onPlayMedia: (com.example.model.VideoItem) -> Unit = {},
) {
    val sources by discoverViewModel.sourceSettings.collectAsState()
    val palette = LocalHiPalette.current
    val account: com.example.auth.GoogleAccountViewModel = viewModel()
    val profile by account.profile.collectAsState()
    var personalStorage by remember { mutableStateOf<String?>(null) }
    var showDrive by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(profile?.uid) {
        showDrive = false
        if (profile != null && showAccount) { showAccount = false; showDrive = true }
    }
    var dialog by remember { mutableStateOf<SourceDialog?>(null) }
    var browserKind by remember { mutableStateOf<String?>(null) }
    var manage by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf<String?>(null) }
    fun setup(which: SourceDialog) { manage = false; browserKind = null; dialog = which }
    fun connected(kind: String) {
        if (kind == "jellyfin") drafts.jellyfin.clear() else drafts.webDav.clear()
        dialog = null; browserKind = kind
    }
    Column(modifier) {
        Text("Connect your cloud storage or personal media server. These connections are optional.",
            color = palette.textSecondary, modifier = Modifier.padding(14.dp))
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated)) {
            Column {
                SourceRow(Icons.Default.Cloud, "Google Drive",
                    if (profile != null) "Browse videos & music · Authorize Drive when opening" else "Your cloud files · Sign in to connect",
                    onAction = { if (profile == null) showAccount = true else showDrive = true })
                SourceDivider()
                SourceRow(Icons.Default.Cloud, "OneDrive", "Personal Microsoft cloud storage · Read-only", onAction = { personalStorage = "onedrive" })
                SourceDivider()
                SourceRow(Icons.Default.Cloud, "Dropbox", "Your Dropbox files · Read-only", onAction = { personalStorage = "dropbox" })
                SourceDivider()
                SourceRow(Icons.Default.Storage, "Network folders (SMB)", "Shared folders on your NAS or computer", onAction = { personalStorage = "smb" })
                SourceDivider()
                SourceRow(Icons.Default.CloudDone, "Jellyfin",
                    sources.jellyfin?.let { "${it.serverName} · Browse library" } ?: "Your personal media server · Connect",
                    onAction = { if (sources.jellyfin?.isConfigured == true) browserKind = "jellyfin" else setup(SourceDialog.JELLYFIN) })
                SourceDivider()
                SourceRow(Icons.Default.Storage, "WebDAV · Nextcloud / ownCloud",
                    sources.webDav?.let { "${it.displayName} · Browse folders" } ?: "Your online storage folder · Connect",
                    onAction = { if (sources.webDav?.isConfigured == true) browserKind = "webdav" else setup(SourceDialog.WEBDAV) })
            }
        }
        TextButton(onClick = { manage = true }) { Text("Manage Jellyfin & WebDAV") }
    }
    personalStorage?.let { PersonalStorageBrowser(it, onDismiss = { personalStorage = null }, onPlay = onPlayMedia) }
    if (showAccount) GoogleAccountDialog(onDismiss = { showAccount = false }, onPlayDriveMedia = onPlayMedia, model = account)
    if (showDrive && profile != null) GoogleDriveBrowserSheet(onDismiss = { showDrive = false }, onPlay = onPlayMedia)
    browserKind?.let { kind ->
        NetworkMediaBrowser(kind, discoverViewModel, onDismiss = { browserKind = null },
            onManage = { browserKind = null; manage = true }, onPlay = onPlayMedia)
    }
    if (manage) androidx.compose.ui.window.Dialog(onDismissRequest = { manage = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = palette.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { manage = false }) { Text("Back") }
                    Text("Network connections", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (sources.jellyfin?.isConfigured == true) {
                        TextButton(onClick = { manage = false; browserKind = "jellyfin" }) { Text("Browse Jellyfin library") }
                        TextButton(onClick = { disconnect = "jellyfin" }) { Text("Disconnect active Jellyfin") }
                    }
                    if (sources.webDav?.isConfigured == true) {
                        TextButton(onClick = { manage = false; browserKind = "webdav" }) { Text("Browse WebDAV folders") }
                        TextButton(onClick = { disconnect = "webdav" }) { Text("Disconnect active WebDAV") }
                    }
                    SavedMediaSources(discoverViewModel, addJellyfin = { setup(SourceDialog.JELLYFIN) }, addWebDav = { setup(SourceDialog.WEBDAV) })
                }
            }
        }
    }
    disconnect?.let { kind ->
        AlertDialog(onDismissRequest = { disconnect = null }, title = { Text("Disconnect active server?") },
            text = { Text("Stops access from this device. Your server files are not deleted. Saved connection copies remain until you forget them in Manage connections.") },
            confirmButton = { TextButton(onClick = {
                disconnect = null
                if (kind == "jellyfin") discoverViewModel.removeJellyfin() else discoverViewModel.removeWebDav()
            }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { disconnect = null }) { Text("Cancel") } })
    }
    when (dialog) {
        SourceDialog.JELLYFIN -> JellyfinQuickConnectDialog(drafts.jellyfin, discoverViewModel,
            onDismiss = { drafts.jellyfin.clear(); dialog = null }, onPassword = { dialog = SourceDialog.JELLYFIN_PASSWORD }, onConnected = { connected("jellyfin") })
        SourceDialog.JELLYFIN_PASSWORD -> JellyfinSetupDialog(drafts.jellyfin,
            onDismiss = { drafts.jellyfin.clear(); dialog = null }, onConnected = { connected("jellyfin") },
            onConnect = { url, user, password, complete -> discoverViewModel.connectJellyfin(url, user, password, complete) })
        SourceDialog.WEBDAV -> WebDavSetupDialog(drafts.webDav,
            onDismiss = { drafts.webDav.clear(); dialog = null }, onConnected = { connected("webdav") },
            onConnect = { url, user, password, name, complete -> discoverViewModel.saveWebDav(url, user, password, name, complete) })
        null -> Unit
    }
}

private enum class SourceDialog { JELLYFIN, JELLYFIN_PASSWORD, WEBDAV }

@Composable
private fun SourceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
    subtitle: String, onAction: () -> Unit) {
    val palette = LocalHiPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onAction).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = palette.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text(subtitle, color = palette.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SourceDivider() {
    val palette = LocalHiPalette.current
    androidx.compose.material3.HorizontalDivider(color = palette.surfaceBorder, modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
private fun JellyfinSetupDialog(
    draft: com.example.ui.feedback.SourceSetupDraft,
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
    onConnect: (String, String, String, (Result<Unit>) -> Unit) -> kotlinx.coroutines.Job
) {
    val palette = LocalHiPalette.current
    var url by draft.url
    var username by draft.username
    var password by draft.password
    var busy by remember { mutableStateOf(false) }
    var allowHttp by remember(url) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val cancelSetup = { connectionJob?.cancel(); onDismiss() }
    DisposableEffect(Unit) { onDispose { connectionJob?.cancel() } }
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        onDismissRequest = cancelSetup,
        containerColor = palette.surface,
        title = { Text("Connect Jellyfin", color = palette.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Use your own free Jellyfin server. Sign-in creates an encrypted device session. Your password is not retained. Quick Connect is also available from the previous screen.", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                SourceTextField(url, { url = it }, "Server URL", "https://jellyfin.example.com", enabled = !busy)
                Spacer(Modifier.height(6.dp))
                SourceTextField(username, { username = it }, "Username", enabled = !busy)
                Spacer(Modifier.height(6.dp))
                SourceTextField(password, { password = it }, "Password (if required)", password = true, enabled = !busy)
                if (url.trim().startsWith("http://", true)) {
                    Text("HTTP is not encrypted. Prefer HTTPS. Only continue on a trusted network and server.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(allowHttp, { allowHttp = it }, enabled = !busy)
                        Text("Allow this unencrypted connection", Modifier.weight(1f))
                    }
                }
                error?.let { Text(it, color = Color(0xFFD14343), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    connectionJob = onConnect(url, username, password) { result ->
                        busy = false
                        result.onSuccess { onConnected() }.onFailure { error = com.example.ui.feedback.UserFacingErrors.report("jellyfin_connect", it, "Couldn't connect. Check the server address, username and password, then try again.") }
                    }
                },
                enabled = !busy && url.isNotBlank() && username.isNotBlank() && (!url.trim().startsWith("http://", true) || allowHttp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
            ) { Text(if (busy) "Connecting…" else "Connect") }
        },
        dismissButton = { TextButton(onClick = cancelSetup) { Text("Cancel", color = palette.textSecondary) } }
    )
}

@Composable
private fun WebDavSetupDialog(
    draft: com.example.ui.feedback.SourceSetupDraft,
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
    onConnect: (String, String, String, String, (Result<Unit>) -> Unit) -> kotlinx.coroutines.Job
) {
    val palette = LocalHiPalette.current
    var url by draft.url
    var name by draft.name
    var username by draft.username
    var password by draft.password
    var busy by remember { mutableStateOf(false) }
    var allowHttp by remember(url) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val cancelSetup = { connectionJob?.cancel(); onDismiss() }
    DisposableEffect(Unit) { onDispose { connectionJob?.cancel() } }
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        onDismissRequest = cancelSetup,
        containerColor = palette.surface,
        title = { Text("Connect WebDAV", color = palette.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Enter the WebDAV folder address from your storage provider, not its website sign-in URL. Use a provider app password if required. Hi Player browses that folder for direct playback.", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Provider setup", color = palette.textPrimary)
                TextButton(enabled = !busy, onClick = { name = "Nextcloud" }) { Text("Nextcloud") }
                TextButton(enabled = !busy, onClick = { name = "ownCloud" }) { Text("ownCloud") }
                if (name == "Nextcloud" || name == "ownCloud") Text("In your $name web interface, open Files → Settings and copy the WebDAV address. Paste that full address below; it may include a server subfolder and your user ID. Use an app password when your provider requires one.", color = palette.textSecondary)
                SourceTextField(url, { url = it }, "Folder URL", "https://cloud.example.com/remote.php/dav/files/me/Videos/", enabled = !busy)
                Spacer(Modifier.height(6.dp))
                SourceTextField(name, { name = it }, "Source name", enabled = !busy)
                Spacer(Modifier.height(6.dp))
                SourceTextField(username, { username = it }, "Username", enabled = !busy)
                Spacer(Modifier.height(6.dp))
                SourceTextField(password, { password = it }, "Password", password = true, enabled = !busy)
                if (url.trim().startsWith("http://", true)) {
                    Text("HTTP is not encrypted. Prefer HTTPS. Only continue on a trusted network and server.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(allowHttp, { allowHttp = it }, enabled = !busy)
                        Text("Allow this unencrypted connection", Modifier.weight(1f))
                    }
                }
                error?.let { Text(it, color = Color(0xFFD14343), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    connectionJob = onConnect(url, username, password, name) { result ->
                        busy = false
                        result.onSuccess { onConnected() }.onFailure { error = com.example.ui.feedback.UserFacingErrors.report("webdav_connect", it, "Couldn't connect. Check the folder address and sign-in details, then try again.") }
                    }
                },
                enabled = !busy && url.isNotBlank() && username.isNotBlank() && password.isNotBlank() && (!url.trim().startsWith("http://", true) || allowHttp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
            ) { Text(if (busy) "Connecting…" else "Connect") }
        },
        dismissButton = { TextButton(onClick = cancelSetup) { Text("Cancel", color = palette.textSecondary) } }
    )
}

@Composable
private fun SourceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    password: Boolean = false,
    enabled: Boolean = true
) {
    val palette = LocalHiPalette.current
    OutlinedTextField(
        enabled = enabled,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.primary,
            unfocusedBorderColor = palette.surfaceBorder,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
            focusedLabelColor = palette.primary,
            unfocusedLabelColor = palette.textSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
