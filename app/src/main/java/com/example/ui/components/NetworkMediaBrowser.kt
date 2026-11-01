package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.*
import com.example.viewmodel.DiscoverViewModel
import com.example.viewmodel.NetworkMediaBrowserViewModel
import com.example.ui.theme.LocalHiPalette

@Composable
internal fun NetworkMediaBrowser(kind: String, sources: DiscoverViewModel, onDismiss: () -> Unit,
    onManage: () -> Unit, onPlay: (VideoItem) -> Unit, model: NetworkMediaBrowserViewModel = viewModel()) {
    val connected by sources.sourceSettings.collectAsState()
    val state by model.state.collectAsState()
    val palette = LocalHiPalette.current
    LaunchedEffect(kind, connected.jellyfin, connected.webDav) {
        if ((kind == "jellyfin" && connected.jellyfin?.isConfigured != true) ||
            (kind == "webdav" && connected.webDav?.isConfigured != true)) { model.clear(); onDismiss() }
        else model.open(kind, connected.jellyfin, connected.webDav)
    }
    DisposableEffect(Unit) { onDispose { model.stop() } }
    Dialog(onDismissRequest = { if (state.path.size > 1) model.back() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(state.path.size > 1) { model.back() }
        Surface(Modifier.fillMaxSize(), color = palette.background, contentColor = palette.textPrimary) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (state.path.size > 1) model.back() else onDismiss() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text(state.path.lastOrNull()?.title ?: "Network media", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onManage) { Text("Manage") }
                }
                Row {
                    TextButton(enabled = !state.busy, onClick = { model.load() }) { Text("Refresh") }
                    if (state.busy) TextButton(onClick = model::stop) { Text("Stop loading") }
                }
                state.message?.let { Text(it); TextButton(enabled = !state.busy, onClick = { model.load() }) { Text("Try again") } }
                state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.kind != kind || (state.busy && state.entries.isEmpty())) ContentSkeleton("Loading media…", Modifier.weight(1f), SkeletonLayout.FILES)
                else {
                    val key = state.kind + ":" + state.path.lastOrNull()?.reference.orEmpty()
                    val listState = remember(key) { val position = model.scrollPosition(key); LazyListState(position.first, position.second) }
                    DisposableEffect(key, listState) { onDispose { model.saveScroll(key, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) } }
                    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                        if (state.entries.isEmpty() && state.message == null && !state.busy) item { Text("No supported media or folders here.", Modifier.padding(vertical = 24.dp)) }
                        items(state.entries, key = { it.id }) { entry ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = !state.busy && (entry.folder || entry.playable != null)) {
                                if (entry.folder) model.enter(entry) else entry.playable?.let { media ->
                                    // The active server's origin/path registry supplies credentials;
                                    // never create an orphan per-file token scope or persist tokens in a URL.
                                    onDismiss(); onPlay(media.toVideoItem())
                                }
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (entry.folder) Icons.Default.Folder else if (entry.playable?.streamMimeType?.startsWith("audio/") == true) Icons.Default.MusicNote else Icons.Default.Movie, null, tint = palette.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text(entry.title); Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
                                if (entry.playable != null) Icon(Icons.Default.PlayArrow, "Play")
                            }
                        }
                        if (state.busy) item { Text("Loading…", Modifier.padding(12.dp)) }
                        if (state.nextOffset != null) item { TextButton(enabled = !state.busy, onClick = { model.load(more = true) }) { Text("Load more") } }
                    }
                }
            }
        }
    }
}
