package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.LocalHiPalette

enum class LibraryScanStatus { IDLE, SCANNING, COMPLETE, INCOMPLETE }

data class LibraryMediaAccess(val granted: Boolean, val limited: Boolean, val error: String?, val openSettings: () -> Unit)

@Composable
fun rememberLibraryMediaAccess(audio: Boolean, onGranted: () -> Unit): LibraryMediaAccess {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onGranted)
    fun readAccess(): Pair<Boolean, Boolean> {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            if (audio) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_MEDIA_VIDEO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        val full = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
        val limited = !audio && !full && Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return (full || limited) to limited
    }
    var access by remember(audio) { mutableStateOf(readAccess()) }
    var opening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        opening = false
        access = readAccess()
        if (access.first) refresh()
    }
    DisposableEffect(owner, audio) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = readAccess()
                if (now != access && now.first) refresh()
                access = now
                opening = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return LibraryMediaAccess(access.first, access.second, error) {
        if (!opening) {
            opening = true
            error = null
            try {
                settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)))
            } catch (failure: Exception) {
                opening = false
                error = "Couldn't open settings. Open Hi Player's app info and allow media access."
            }
        }
    }
}

@Composable
fun LibraryPermissionPanel(audio: Boolean, onOpenSettings: () -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    val palette = LocalHiPalette.current
    val media = if (audio) "music" else "videos"
    Box(modifier.fillMaxSize().testTag("library_permission_panel"), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Folder, null, tint = palette.primary, modifier = Modifier.size(48.dp))
            Text("Your $media belong here", color = palette.textPrimary, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text("Allow media access in Settings → Permissions. Return here and Hi Player will scan in the background. You can keep using the browser and other tabs without granting access.",
                color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            error?.let { Text(it, color = palette.textSecondary, textAlign = TextAlign.Center) }
            Button(onClick = onOpenSettings, modifier = Modifier.testTag("library_allow_access")) {
                Text("Allow access", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun LibraryScanFeedback(status: LibraryScanStatus, count: Int, audio: Boolean,
                        centered: Boolean = false, modifier: Modifier = Modifier) {
    if (status == LibraryScanStatus.IDLE) return
    val palette = LocalHiPalette.current
    val scanning = status == LibraryScanStatus.SCANNING
    val media = if (audio) "audio tracks" else "videos"
    val message = when (status) {
        LibraryScanStatus.SCANNING -> "Scanning $media…"
        LibraryScanStatus.COMPLETE -> "Scan complete · $count $media available"
        else -> "Scan couldn't finish. Refresh to try again."
    }
    if (centered) {
        Column(modifier.fillMaxSize().wrapContentSize(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (scanning) CircularProgressIndicator(modifier = Modifier.size(56.dp).testTag("library_scan_circle"), color = palette.primary, trackColor = palette.primary.copy(alpha = 0.15f))
            else Icon(Icons.Default.CheckCircle, null, tint = palette.primary)
            Text(message, color = palette.textPrimary, textAlign = TextAlign.Center)
            if (scanning) Text("You can switch tabs while the library updates.", color = palette.textSecondary, textAlign = TextAlign.Center)
        }
    } else {
        Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (scanning) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.primary, trackColor = palette.primary.copy(alpha = 0.15f))
            else if (status == LibraryScanStatus.COMPLETE) Icon(Icons.Default.CheckCircle, null, tint = palette.primary, modifier = Modifier.size(20.dp))
            Text(message, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Stable
class FirstUseTipState(private val context: Context, private val key: String) {
    var visible by mutableStateOf(!context.getSharedPreferences("first_use_tips", Context.MODE_PRIVATE).getBoolean(key, false))
        private set
    fun dismiss() {
        visible = false
        context.getSharedPreferences("first_use_tips", Context.MODE_PRIVATE).edit().putBoolean(key, true).apply()
    }
}

@Composable
fun rememberFirstUseTip(key: String): FirstUseTipState {
    val context = LocalContext.current.applicationContext
    return remember(context, key) { FirstUseTipState(context, key) }
}

@Composable
fun FirstUseTip(state: FirstUseTipState, title: String, message: String,
                modifier: Modifier = Modifier, overVideo: Boolean = false) {
    if (!state.visible) return
    val palette = LocalHiPalette.current
    Surface(modifier = modifier.testTag("first_use_tip"), shape = RoundedCornerShape(12.dp),
        color = if (overVideo) Color(0xED101820) else palette.primary.copy(alpha = 0.09f)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = if (overVideo) Color.White else palette.textPrimary, fontSize = 16.sp)
            Text(message, color = if (overVideo) Color.White.copy(alpha = 0.9f) else palette.textSecondary, fontSize = 14.sp)
            TextButton(onClick = state::dismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Got it", color = if (overVideo) Color.White else palette.primary)
            }
        }
    }
}
