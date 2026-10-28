package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.auth.GoogleAccountViewModel
import com.example.auth.SecureWebSessions
import com.example.ui.screens.HiProviderIcon
import com.example.ui.theme.LocalHiPalette

private tailrec fun Context.accountActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.accountActivity()
    else -> null
}

@Composable
internal fun GoogleAccountCard(model: GoogleAccountViewModel = viewModel()) {
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    val profile by model.profile.collectAsState()
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
            Text("Website logins use a separate secure-browser session. Signing out here does not sign you out of websites. No Gmail inbox or Drive access is requested by this sign-in.", color = palette.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Google" to SecureWebSessions.GOOGLE_ACCOUNT, "YouTube" to SecureWebSessions.YOUTUBE, "Gmail" to SecureWebSessions.GMAIL).forEach { (label, url) ->
                    TextButton(onClick = { SecureWebSessions.open(context, url, palette.surface.toArgb()).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() } }) { Text(label) }
                }
            }
        }
    }
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
