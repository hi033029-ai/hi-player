package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.auth.GoogleAccountViewModel
import com.example.ui.screens.HiProviderIcon
import com.example.ui.theme.LocalHiPalette

private tailrec fun Context.accountActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.accountActivity()
    else -> null
}

@Composable
internal fun GoogleAccountCard(onPlayDriveMedia: (com.example.model.VideoItem) -> Unit = {}, model: GoogleAccountViewModel = viewModel(),
    includeDriveConnection: Boolean = true) {
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    var showDrive by remember { mutableStateOf(false) }
    val profile by model.profile.collectAsState()
    LaunchedEffect(profile?.uid) { showDrive = false }
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.surfaceBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val photo = profile?.photoUrl
                if (photo != null) AsyncImage(photo, "Google account profile photo", Modifier.size(48.dp).clip(CircleShape))
                else Icon(Icons.Default.AccountCircle, null, Modifier.size(48.dp), tint = palette.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.name?.ifBlank { "Google account" } ?: "Hi Player Google account", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    Text(profile?.email ?: "Official Google account chooser and authentication", color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (profile == null) {
                // Google's neutral button treatment and existing unmodified Google G artwork.
                OutlinedButton(enabled = model.configured && !busy, onClick = { context.accountActivity()?.let(model::signIn) },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                    border = BorderStroke(1.dp, Color(0xFF747775))) {
                    HiProviderIcon("Google", 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (busy) "Signing in…" else "Sign in with Google")
                }
                if (!model.configured) Text("Google sign-in setup required: configure the OAuth Web client ID and enable Google in Firebase Authentication.", color = palette.textSecondary)
            } else {
                Text("Signed in to Hi Player. Your session is managed by Firebase Authentication.", color = palette.textSecondary)
                OutlinedButton(enabled = !busy, onClick = model::signOut) { Text("Sign out of Hi Player") }
            }
            message?.let { Text(it, color = palette.textSecondary) }
            Text("Google sign-in connects your Hi Player profile. Drive permission is requested only when you open your files. Website sessions remain browser-managed.", color = palette.textSecondary)
            if (includeDriveConnection && profile != null) FilledTonalButton(enabled = !busy, onClick = { showDrive = true }) { Text("My Drive · videos & music") }

        }
    }
    if (showDrive && profile != null) GoogleDriveBrowserSheet(onDismiss = { showDrive = false }, onPlay = onPlayDriveMedia)
}

@Composable
internal fun GoogleAccountAvatar(onClick: () -> Unit, model: GoogleAccountViewModel = viewModel()) {
    val profile by model.profile.collectAsState()
    val palette = LocalHiPalette.current
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        val photo = profile?.photoUrl
        if (photo != null) AsyncImage(photo, "Google account settings", Modifier.size(30.dp).clip(CircleShape))
        else Icon(Icons.Default.AccountCircle, "Google account settings", tint = palette.textPrimary)
    }
}

/** Same activity-scoped Firebase account as Settings; never a WebView login. */
@Composable
internal fun GoogleAccountDialog(
    onDismiss: () -> Unit,
    onPlayDriveMedia: (com.example.model.VideoItem) -> Unit = {},
    model: GoogleAccountViewModel = viewModel(),
) {
    val palette = LocalHiPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.background,
        title = { Text("Hi Player account", color = palette.textPrimary) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                GoogleAccountCard(onPlayDriveMedia = onPlayDriveMedia, model = model)
                Text("This account is shared across Hi Player settings, browser controls and the player. Websites use their own sign-in sessions; authorizing the app does not sign you into Google websites.", color = palette.textSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** App identity only: never implies the displayed website has authenticated. */
@Composable
internal fun GoogleAccountStatusRow(
    profile: com.example.auth.GoogleAccountProfile?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHiPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val photo = profile?.photoUrl
        if (photo != null) AsyncImage(photo, "Hi Player account photo", Modifier.size(40.dp).clip(CircleShape))
        else Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp), tint = palette.textPrimary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (profile != null) "Signed in to Hi Player" else "Hi Player account: not signed in",
                color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            if (profile != null) {
                if (profile.name.isNotBlank()) Text(profile.name, color = palette.textPrimary)
                if (profile.email.isNotBlank()) Text(profile.email, color = palette.textPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
