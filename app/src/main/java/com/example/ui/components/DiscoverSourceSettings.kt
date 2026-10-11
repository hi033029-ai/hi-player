package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.DiscoverViewModel

/** Source setup for the free Discover feature. TMDB has metadata only; the
 * media sources below are user-owned Jellyfin/WebDAV libraries. */
@Composable
fun DiscoverSourceSettings(
    discoverViewModel: DiscoverViewModel,
    tmdbApiKey: String,
    onSaveTmdbApiKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sources by discoverViewModel.sourceSettings.collectAsState()
    val palette = LocalHiPalette.current
    var dialog by remember { mutableStateOf<SourceDialog?>(null) }

    Column(modifier = modifier) {
        Text(
            text = "DISCOVER & PERSONAL MEDIA SOURCES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.primary,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
        Text(
            text = "TMDB provides free title information. Playback searches only your own Jellyfin server or WebDAV folder; no paid or third-party catalog is added.",
            color = palette.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 0.dp)
        )
        Spacer(Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SourceRow(
                    icon = Icons.Default.Key,
                    title = "TMDB metadata key",
                    subtitle = if (tmdbApiKey.isBlank()) "Required for movie and season search. Free personal key." else "Free key saved on this device",
                    action = if (tmdbApiKey.isBlank()) "Add key" else "Change",
                    onAction = { dialog = SourceDialog.TMDB }
                )
                SourceDivider()
                SourceRow(
                    icon = Icons.Default.CloudDone,
                    title = "Jellyfin server",
                    subtitle = sources.jellyfin?.let { "Connected: ${it.serverName}" }
                        ?: "Connect your free Jellyfin server and its own library",
                    action = if (sources.jellyfin?.isConfigured == true) "Remove" else "Connect",
                    destructive = sources.jellyfin?.isConfigured == true,
                    onAction = {
                        if (sources.jellyfin?.isConfigured == true) discoverViewModel.removeJellyfin()
                        else dialog = SourceDialog.JELLYFIN
                    }
                )
                SourceDivider()
                SourceRow(
                    icon = Icons.Default.Storage,
                    title = "WebDAV folder",
                    subtitle = sources.webDav?.let { "Connected: ${it.displayName}" }
                        ?: "Connect a folder you control for direct playback",
                    action = if (sources.webDav?.isConfigured == true) "Remove" else "Connect",
                    destructive = sources.webDav?.isConfigured == true,
                    onAction = {
                        if (sources.webDav?.isConfigured == true) discoverViewModel.removeWebDav()
                        else dialog = SourceDialog.WEBDAV
                    }
                )
            }
        }
    }

    when (dialog) {
        SourceDialog.TMDB -> TmdbKeySetupDialog(
            currentValue = tmdbApiKey,
            onDismiss = { dialog = null },
            onSave = {
                onSaveTmdbApiKey(it)
                dialog = null
            }
        )
        SourceDialog.JELLYFIN -> JellyfinSetupDialog(
            onDismiss = { dialog = null },
            onConnect = { baseUrl, username, password, onComplete ->
                discoverViewModel.connectJellyfin(baseUrl, username, password, onComplete)
            }
        )
        SourceDialog.GOOGLE_DRIVE -> GoogleDriveSetupDialog(
            current = sources.googleDrive,
            onDismiss = { dialog = null },
            onSave = { account, token, name, onComplete ->
                discoverViewModel.saveGoogleDrive(account, token, name, onComplete)
            }
        )
        SourceDialog.WEBDAV -> WebDavSetupDialog(
            onDismiss = { dialog = null },
            onConnect = { url, username, password, name, onComplete ->
                discoverViewModel.saveWebDav(url, username, password, name, onComplete)
            }
        )
        null -> Unit
    }
}

private enum class SourceDialog { TMDB, JELLYFIN, WEBDAV, GOOGLE_DRIVE }

@Composable
private fun SourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: String,
    destructive: Boolean = false,
    onAction: () -> Unit
) {
    val palette = LocalHiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = palette.textSecondary, fontSize = 10.5.sp)
        }
        Text(
            text = action,
            color = if (destructive) Color(0xFFD14343) else palette.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.clickable(onClick = onAction).padding(7.dp)
        )
    }
}

@Composable
private fun SourceDivider() {
    val palette = LocalHiPalette.current
    androidx.compose.material3.HorizontalDivider(color = palette.surfaceBorder, modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
private fun TmdbKeySetupDialog(currentValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val palette = LocalHiPalette.current
    var value by remember { mutableStateOf(currentValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Free TMDB API key", color = palette.textPrimary) },
        text = {
            Column {
                Text("TMDB is used only for movie and TV metadata. Create a free personal key at themoviedb.org/settings/api.", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                SourceTextField(value = value, onValueChange = { value = it }, label = "TMDB API key")
            }
        },
        confirmButton = {
            Button(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = palette.primary)) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) } }
    )
}

@Composable
private fun JellyfinSetupDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String, (Result<Unit>) -> Unit) -> Unit
) {
    val palette = LocalHiPalette.current
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = palette.surface,
        title = { Text("Connect Jellyfin", color = palette.textPrimary) },
        text = {
            Column {
                Text("Use your own free Jellyfin server. Login creates a device session stored encrypted on this device.", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                SourceTextField(url, { url = it }, "Server URL", "https://jellyfin.example.com")
                Spacer(Modifier.height(6.dp))
                SourceTextField(username, { username = it }, "Username")
                Spacer(Modifier.height(6.dp))
                SourceTextField(password, { password = it }, "Password (if required)", password = true)
                error?.let { Text(it, color = Color(0xFFD14343), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    onConnect(url, username, password) { result ->
                        busy = false
                        result.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Could not connect to Jellyfin." }
                    }
                },
                enabled = !busy && url.isNotBlank() && username.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
            ) { Text(if (busy) "Connecting…" else "Connect") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) } }
    )
}

@Composable
private fun WebDavSetupDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String, String, (Result<Unit>) -> Unit) -> Unit
) {
    val palette = LocalHiPalette.current
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("WebDAV") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = palette.surface,
        title = { Text("Connect WebDAV", color = palette.textPrimary) },
        text = {
            Column {
                Text("Enter a folder URL you control. Hi Player lists only that folder and its nearby subfolders for direct playback.", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                SourceTextField(url, { url = it }, "Folder URL", "https://cloud.example.com/remote.php/dav/files/me/Videos/")
                Spacer(Modifier.height(6.dp))
                SourceTextField(name, { name = it }, "Source name")
                Spacer(Modifier.height(6.dp))
                SourceTextField(username, { username = it }, "Username")
                Spacer(Modifier.height(6.dp))
                SourceTextField(password, { password = it }, "Password", password = true)
                error?.let { Text(it, color = Color(0xFFD14343), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    onConnect(url, username, password, name) { result ->
                        busy = false
                        result.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Could not connect to WebDAV." }
                    }
                },
                enabled = !busy && url.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
            ) { Text(if (busy) "Connecting…" else "Connect") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) } }
    )
}

@Composable
private fun SourceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    password: Boolean = false
) {
    val palette = LocalHiPalette.current
    OutlinedTextField(
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

@Composable
private fun GoogleDriveSetupDialog(
    current: com.example.model.GoogleDriveConnection?,
    onDismiss: () -> Unit,
    onSave: (account: String, token: String, name: String, onComplete: (Result<Unit>) -> Unit) -> Unit
) {
    val palette = LocalHiColorPalette.current
    var account by remember { mutableStateOf(current?.accountName.orEmpty()) }
    var token by remember { mutableStateOf(current?.accessToken.orEmpty()) }
    var name by remember { mutableStateOf(current?.displayName ?: "Google Drive") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceCard,
        title = { Text("Connect Google Drive", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Connect your Google Drive account using an OAuth 2.0 access token to stream videos without downloading.",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Google Account Email") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("OAuth Access Token") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
                error?.let {
                    Text(text = it, color = palette.accentRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && token.isNotBlank(),
                onClick = {
                    isSaving = true
                    error = null
                    onSave(account, token, name) { result ->
                        isSaving = false
                        result.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Could not connect to Google Drive." }
                    }
                }
            ) {
                Text(if (isSaving) "Verifying..." else "Connect", color = palette.accentYellow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textSecondary)
            }
        }
    )
}
