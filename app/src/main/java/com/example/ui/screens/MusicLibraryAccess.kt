package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.LocalHiPalette

@Composable
internal fun MusicLibraryAccess(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val palette = LocalHiPalette.current
    val refresh by rememberUpdatedState(onRefresh)
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun granted() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
    var access by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = granted()
        refresh()
    }
    DisposableEffect(owner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = granted()
                if (now && !access) refresh()
                access = now
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!access) {
        Surface(border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder), color = palette.primary.copy(alpha = .08f), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Allow access to your music", color = palette.textPrimary)
                Text("Music access is separate from photos and videos. Grant it to list audio files on this device.", color = palette.textSecondary)
                Row {
                    OutlinedButton(onClick = { launcher.launch(permission) }) { Text("Allow music access") }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Settings") }
                }
            }
        }
    }
}
