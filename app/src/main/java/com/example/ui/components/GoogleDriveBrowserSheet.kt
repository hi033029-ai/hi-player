package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drive.*
import com.example.model.VideoItem
import com.example.ui.theme.LocalHiPalette

private tailrec fun Context.driveActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.driveActivity(); else -> null }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoogleDriveBrowserSheet(onDismiss: () -> Unit, onPlay: (VideoItem) -> Unit, model: GoogleDriveViewModel = viewModel()) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val state by model.state.collectAsState()
    var search by remember { mutableStateOf("") }
    LaunchedEffect(state.query) { search = state.query }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { model.authorizationResult(it.resultCode, it.data) }
    val connect = { context.driveActivity()?.let { activity -> model.connect(activity) { resolution.launch(IntentSenderRequest.Builder(it.intentSender).build()) } }; Unit }
    BackHandler(state.connected && (state.path.size > 1 || state.query.isNotBlank())) { search = ""; model.back() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = 640.dp, containerColor = palette.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.path.size > 1 || state.query.isNotBlank()) IconButton(onClick = { search = ""; model.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous Drive folder", tint = palette.textPrimary) }
                Column(Modifier.weight(1f)) {
                    Text(state.path.last().name, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                    Text(GoogleDriveSession.owner()?.email ?: "Sign in first", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.connected) IconButton(enabled = !state.busy, onClick = { model.load() }) { Icon(Icons.Default.Refresh, "Refresh Drive", tint = palette.textPrimary) }
            }
            if (com.example.BuildConfig.GOOGLE_DRIVE_ENABLED) {
                Text("Google Drive access requires Google authorization. If access is blocked, reconnect only after the project consent configuration is approved.", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            if (!state.connected) {
                Text("Browse and play videos and music from your signed-in Google account. Google will ask for read-only access to your Drive files. Hi Player cannot change or delete them.", color = palette.textSecondary)
                Spacer(Modifier.height(12.dp))
                if (!com.example.BuildConfig.GOOGLE_DRIVE_ENABLED) Text("Drive setup is awaiting Google's restricted-scope review. Your Google login is unaffected.", color = palette.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
                Button(enabled = com.example.BuildConfig.GOOGLE_DRIVE_ENABLED && !state.busy, onClick = connect) { Text(if (state.busy) "Connecting…" else "Allow read-only Drive access") }
            } else {
                OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true, label = { Text("Search Drive media") }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(enabled = !state.busy, onClick = { model.search(search) }) { Icon(Icons.Default.Search, "Search Drive") } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !state.busy, onClick = connect) { Text("Reconnect") }
                    TextButton(enabled = !state.busy, onClick = model::disconnect) { Text("Disconnect Drive") }
                }
            }
            state.message?.let { Text(it, color = palette.textSecondary, modifier = Modifier.padding(vertical = 8.dp)) }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.primary)
            if (state.connected && !state.busy && state.files.isEmpty() && state.message == null) Text("No video or music files in this folder.", color = palette.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
            if (state.busy && state.connected && state.files.isEmpty()) {
                ContentSkeleton(label = "Loading Drive files…", modifier = Modifier.weight(1f), layout = SkeletonLayout.FILES)
            } else LazyColumn(Modifier.weight(1f)) {
                items(state.files, key = { it.id }) { file ->
                    val enabled = !state.busy && (file.isFolder || file.isPlayable && file.canDownload)
                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        if (file.isFolder) model.open(file) else model.preparePlayback(file) { selected ->
                            onDismiss()
                            onPlay(VideoItem(uri = android.net.Uri.parse(GoogleDrivePolicy.mediaUrl(selected.id)), title = selected.name,
                                mimeType = selected.mimeType, sizeBytes = selected.size, path = "drive:${selected.id}", folderName = "Google Drive"))
                        }
                    }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (file.isFolder) Icons.Default.Folder else if (file.mimeType.startsWith("audio/")) Icons.Default.MusicNote else Icons.Default.Movie,
                            null, tint = palette.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (file.isFolder) "Folder" else if (!file.canDownload) "Playback restricted by file owner" else com.example.ui.screens.downloadBytes(file.size), color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        if (file.isPlayable && file.canDownload) Icon(Icons.Default.PlayArrow, "Play ${file.name}", tint = palette.textSecondary)
                    }
                }
                if (state.nextPage != null) item { TextButton(enabled = !state.busy, onClick = { model.load(more = true) }) { Text("Load more") } }
            }
        }
    }
}
