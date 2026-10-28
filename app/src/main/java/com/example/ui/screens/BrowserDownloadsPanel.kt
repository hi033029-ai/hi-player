package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.downloads.*
import com.example.ui.theme.LocalHiPalette

@Composable
internal fun BrowserDownloadsPanel(downloads: List<BrowserDownload>, query: String, onPlay: (Long) -> Unit) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    var filter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("Newest first") }
    var filterMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var bulkMenu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<BrowserDownload?>(null) }
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf<BrowserDownload?>(null) }
    val shown = downloads.filter { d ->
        (query.isBlank() || d.fileName.contains(query, true)) && when (filter) {
            "In progress" -> d.isActive
            "Completed" -> d.status == BrowserDownloadStatus.COMPLETED
            "Failed" -> d.status == BrowserDownloadStatus.FAILED
            else -> true
        }
    }.let { list -> when (sort) {
        "Oldest first" -> list.sortedBy { it.id }
        "Name" -> list.sortedBy { it.fileName.lowercase() }
        "Largest first" -> list.sortedByDescending { maxOf(it.totalBytes, it.downloadedBytes) }
        else -> list.sortedByDescending { it.id }
    } }
    val running = downloads.filter { it.status == BrowserDownloadStatus.RUNNING }
    val speed = running.sumOf { it.speedBytesPerSecond }
    val knownSize = running.isNotEmpty() && running.all { it.totalBytes > 0 }
    val eta = if (knownSize && speed > 0) running.sumOf { (it.totalBytes - it.downloadedBytes).coerceAtLeast(0) } / speed else null
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (downloads.any { it.isActive }) "In progress" else "Downloads", color = palette.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sort downloads", tint = palette.textPrimary) }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf("Newest first", "Oldest first", "Name", "Largest first").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { sort = value; sortMenu = false }) }
                }
            }
            Box {
                IconButton(onClick = { filterMenu = true }) { Icon(Icons.Default.FilterList, "Filter downloads: $filter", tint = palette.textPrimary) }
                DropdownMenu(filterMenu, { filterMenu = false }) {
                    listOf("All", "In progress", "Completed", "Failed").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { filter = value; filterMenu = false }) }
                }
            }
            Box {
                IconButton(onClick = { bulkMenu = true }) { Icon(Icons.Default.MoreVert, "Download actions", tint = palette.textPrimary) }
                DropdownMenu(bulkMenu, { bulkMenu = false }) {
                    DropdownMenuItem(text = { Text("Pause all") }, enabled = downloads.any { it.needsService }, onClick = { BrowserDownloads.pauseAll(context); bulkMenu = false })
                    DropdownMenuItem(text = { Text("Resume all") }, enabled = downloads.any { it.status == BrowserDownloadStatus.PAUSED || it.status == BrowserDownloadStatus.FAILED }, onClick = { BrowserDownloads.resumeAll(context); bulkMenu = false })
                    DropdownMenuItem(text = { Text("Clear finished history") }, onClick = { BrowserDownloads.clearCompleted(context); bulkMenu = false })
                }
            }
        }
        if (running.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, null, tint = palette.textSecondary, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(6.dp))
            Text("${downloadBytes(speed)}/s", color = palette.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(18.dp))
            Icon(Icons.Default.Schedule, null, tint = palette.textSecondary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(downloadEta(eta), color = palette.textSecondary, fontSize = 12.sp)
        }
        if (filter != "All") Text(filter, color = palette.textSecondary, fontSize = 12.sp)
        if (shown.isEmpty()) Text(if (downloads.isEmpty()) "Your downloads will appear here." else "No matching downloads", color = palette.textSecondary, modifier = Modifier.padding(18.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { download ->
                DownloadProgressRow(download, onPlay = { onPlay(download.id) }, onRename = { rename = download; name = download.fileName }, onDetails = { details = download })
            }
        }
    }
    rename?.let { download ->
        AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename file") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { BrowserDownloads.rename(context, download.id, name); rename = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } })
    }
    details?.let { original ->
        val d = downloads.firstOrNull { it.id == original.id } ?: original
        AlertDialog(onDismissRequest = { details = null }, title = { Text("Download details") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(d.fileName); Text("Status: ${d.status.name.lowercase()}")
                Text("Saved to: ${d.saveLocation}")
                Text("${downloadBytes(d.downloadedBytes)} / ${if (d.totalBytes > 0) downloadBytes(d.totalBytes) else "unknown size"}")
                Text("Type: ${d.mimeType ?: "Detecting"}")
                Text("Server: ${runCatching { java.net.URI(d.sourceUrl).host }.getOrDefault("")}")
                d.error?.let { Text(it) }
                if (d.status != BrowserDownloadStatus.COMPLETED && d.isVideo) Text("Play while downloading depends on the container. Some MP4 files need the end of the file first.")
            } }, confirmButton = { TextButton(onClick = { details = null }) { Text("Done") } })
    }
}

@Composable
private fun DownloadProgressRow(download: BrowserDownload, onPlay: () -> Unit, onRename: () -> Unit, onDetails: () -> Unit) {
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    var menu by remember(download.id) { mutableStateOf(false) }
    val fraction = if (download.totalBytes > 0) (download.downloadedBytes.toDouble() / download.totalBytes).toFloat().coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(palette.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (download.isVideo) Icons.Default.Movie else Icons.Default.InsertDriveFile, null, tint = palette.primary)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(download.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${downloadBytes(download.downloadedBytes)}${if (download.totalBytes > 0) " / ${downloadBytes(download.totalBytes)}" else ""}", color = palette.textSecondary, fontSize = 12.sp)
                Text(when {
                    download.finalizing -> "Saving to ${download.saveLocation}…"
                    download.status == BrowserDownloadStatus.RUNNING -> download.error ?: "${downloadBytes(download.speedBytesPerSecond)}/s • ${downloadEta(download.etaSeconds)}"
                    download.status == BrowserDownloadStatus.COMPLETED -> download.saveLocation
                    else -> download.error ?: download.status.name.lowercase().replaceFirstChar { it.uppercase() }
                }, color = palette.textSecondary, fontSize = 11.sp, maxLines = 2)
            }
            when (download.status) {
                BrowserDownloadStatus.RUNNING, BrowserDownloadStatus.QUEUED -> IconButton(onClick = { BrowserDownloads.pause(context, download.id) }) { Icon(Icons.Default.Pause, "Pause download", tint = palette.textPrimary) }
                BrowserDownloadStatus.PAUSED, BrowserDownloadStatus.FAILED, BrowserDownloadStatus.CANCELLED -> IconButton(onClick = { BrowserDownloads.retry(context, download.id) }) { Icon(Icons.Default.PlayArrow, "Resume or retry download", tint = palette.textPrimary) }
                BrowserDownloadStatus.COMPLETED -> IconButton(onClick = { if (download.isVideo) onPlay() else BrowserDownloads.open(context, download.id) }) { Icon(Icons.Default.PlayCircle, "Open downloaded file", tint = palette.textPrimary) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${download.fileName}", tint = palette.textPrimary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (download.isVideo && download.downloadedBytes > 0) DropdownMenuItem(text = { Text(if (download.status == BrowserDownloadStatus.COMPLETED) "Play" else "Play while downloading") }, onClick = { menu = false; onPlay() })
                    if (download.status == BrowserDownloadStatus.COMPLETED) {
                        DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; BrowserDownloads.open(context, download.id) })
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; BrowserDownloads.share(context, download.id) })
                    }
                    DropdownMenuItem(text = { Text("Rename") }, enabled = !download.finalizing, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Copy download link") }, onClick = { menu = false; BrowserDownloads.copyLink(context, download.id) })
                    DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onDetails() })
                    if (download.isActive) DropdownMenuItem(text = { Text("Cancel download") }, onClick = { menu = false; BrowserDownloads.cancel(context, download.id) })
                    else DropdownMenuItem(text = { Text("Remove from history") }, onClick = { menu = false; BrowserDownloads.removeFromList(context, download.id) })
                }
            }
        }
        if (download.isActive) {
            if (download.status == BrowserDownloadStatus.RUNNING && download.totalBytes <= 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = palette.primary, trackColor = palette.surfaceBorder)
            else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp), color = palette.primary, trackColor = palette.surfaceBorder)
        }
    }
}
