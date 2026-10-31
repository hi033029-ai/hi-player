package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.DiscoverViewModel

/** Source setup for the free Discover feature. The catalog works without an
 * account; playback searches only media sources the user has connected. */
@Composable
fun DiscoverSourceSettings(
    discoverViewModel: DiscoverViewModel,
    modifier: Modifier = Modifier
) {
    val sources by discoverViewModel.sourceSettings.collectAsState()
    val palette = LocalHiPalette.current
    var dialog by remember { mutableStateOf<SourceDialog?>(null) }

    Column(modifier = modifier) {
        Card(
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
        SourceDialog.JELLYFIN -> JellyfinSetupDialog(
            onDismiss = { dialog = null },
            onConnect = { baseUrl, username, password, onComplete ->
                discoverViewModel.connectJellyfin(baseUrl, username, password, onComplete)
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

private enum class SourceDialog { JELLYFIN, WEBDAV }

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
private fun JellyfinSetupDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String, (Result<Unit>) -> Unit) -> kotlinx.coroutines.Job
) {
    val palette = LocalHiPalette.current
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
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
                    connectionJob = onConnect(url, username, password) { result ->
                        busy = false
                        result.onSuccess { onDismiss() }.onFailure { error = com.example.ui.feedback.UserFacingErrors.report("jellyfin_connect", it, "Couldn't connect. Check the server address, username and password, then try again.") }
                    }
                },
                enabled = !busy && url.isNotBlank() && username.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
            ) { Text(if (busy) "Connecting…" else "Connect") }
        },
        dismissButton = { TextButton(onClick = cancelSetup) { Text("Cancel", color = palette.textSecondary) } }
    )
}

@Composable
private fun WebDavSetupDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String, String, (Result<Unit>) -> Unit) -> kotlinx.coroutines.Job
) {
    val palette = LocalHiPalette.current
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("WebDAV") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
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
                    connectionJob = onConnect(url, username, password, name) { result ->
                        busy = false
                        result.onSuccess { onDismiss() }.onFailure { error = com.example.ui.feedback.UserFacingErrors.report("webdav_connect", it, "Couldn't connect. Check the folder address and sign-in details, then try again.") }
                    }
                },
                enabled = !busy && url.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
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
