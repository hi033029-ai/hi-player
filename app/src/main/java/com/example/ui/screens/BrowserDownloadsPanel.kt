package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.HiCinemaBlack
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiSurfaceBorder
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary

@Composable
internal fun BrowserDownloadsPanel(downloads: List<BrowserDownload>, query: String, onPlay: (Long) -> Unit) =
    BrowserDownloadsPanel(downloads, query, onCancelGecko = {}, onPlay = onPlay)

@Composable
internal fun BrowserDownloadsPanel(downloads: List<BrowserDownload>, query: String, onCancelGecko: (Long) -> Unit, onPlay: (Long) -> Unit) {
    val context = LocalContext.current
    var rename by remember { mutableStateOf<BrowserDownload?>(null) }
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf<BrowserDownload?>(null) }
    val shown = downloads.filter { query.isBlank() || it.fileName.contains(query, true) }.sortedByDescending { it.id }
    Column(Modifier.fillMaxSize().background(HiCinemaBlack)) {
        if (shown.isEmpty()) Text(if (downloads.isEmpty()) "Your downloads will appear here." else "No matching downloads", color = HiTextSecondary, modifier = Modifier.padding(18.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { download ->
                DownloadProgressRow(download, onPlay = { onPlay(download.id) }, onCancelGecko = { onCancelGecko(download.id) }, onRename = { rename = download; name = download.fileName }, onDetails = { details = download })
            }
        }
    }
    rename?.let { download ->
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        onDismissRequest = { rename = null }, title = { Text("Rename file") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = HiTextPrimary, unfocusedTextColor = HiTextPrimary,
                    focusedBorderColor = HiPrimaryCyan, unfocusedBorderColor = HiSurfaceBorder)) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { BrowserDownloads.rename(context, download.id, name); rename = null }) { Text("Save", color = HiPrimaryCyan) } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel", color = HiTextSecondary) } },
            containerColor = HiSurfaceElevated, titleContentColor = HiTextPrimary, textContentColor = HiTextSecondary)
    }
    details?.let { original ->
        val d = downloads.firstOrNull { it.id == original.id } ?: original
        AlertDialog(
            modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        onDismissRequest = { details = null }, title = { Text("Download details") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(d.fileName, color = HiTextPrimary); Text("Status: ${d.status.name.lowercase()}")
                Text("Saved to: ${d.saveLocation}")
                Text("${downloadBytes(d.downloadedBytes)} / ${if (d.totalBytes > 0) downloadBytes(d.totalBytes) else "unknown size"}")
                Text("Type: ${d.mimeType ?: "Detecting"}")
                if (d.sourceUrl.isNotBlank()) Text("Server: ${runCatching { java.net.URI(d.sourceUrl).host }.getOrDefault("")}")
                d.error?.let { Text(it) }
                if (d.status != BrowserDownloadStatus.COMPLETED && d.isVideo) Text(if (d.ephemeral) "You can play while downloading. This browser stream cannot be paused or resumed; cancelling removes the partial file. Some MP4 files need the end of the file first." else "Play while downloading depends on the container. Some MP4 files need the end of the file first.")
            } }, confirmButton = { TextButton(onClick = { details = null }) { Text("Done", color = HiPrimaryCyan) } },
            containerColor = HiSurfaceElevated, titleContentColor = HiTextPrimary, textContentColor = HiTextSecondary)
    }
}

@Composable
private fun DownloadProgressRow(download: BrowserDownload, onPlay: () -> Unit, onCancelGecko: () -> Unit, onRename: () -> Unit, onDetails: () -> Unit) {
    val context = LocalContext.current
    var menu by remember(download.id) { mutableStateOf(false) }
    val fraction = if (download.totalBytes > 0) (download.downloadedBytes.toDouble() / download.totalBytes).toFloat().coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("download-card-${download.id}").background(HiSurfaceDark, RoundedCornerShape(18.dp)).border(1.dp, HiSurfaceBorder, RoundedCornerShape(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().testTag("download-main-row-${download.id}"), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(HiPrimaryCyan.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (download.isVideo) Icons.Default.Movie else Icons.Default.InsertDriveFile, null, tint = HiPrimaryCyan)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(download.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis, color = HiTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${downloadBytes(download.downloadedBytes)}${if (download.totalBytes > 0) " / ${downloadBytes(download.totalBytes)}" else ""}", color = HiTextSecondary, fontSize = 12.sp)
                Text(when {
                    download.finalizing -> "Saving to ${download.saveLocation}…"
                    download.status == BrowserDownloadStatus.RUNNING -> download.error ?: if (download.totalBytes > 0) "Downloading • ${(fraction * 100).toInt()}%" else "Downloading"
                    download.status == BrowserDownloadStatus.COMPLETED -> download.saveLocation
                    else -> download.error ?: download.status.name.lowercase().replaceFirstChar { it.uppercase() }
                }, color = HiTextSecondary, fontSize = 11.sp, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            if (download.status == BrowserDownloadStatus.RUNNING && !download.finalizing) Column(Modifier.widthIn(max = 90.dp).testTag("download-stats-${download.id}").padding(end = 6.dp), horizontalAlignment = Alignment.End) {
                Text("${downloadBytes(download.speedBytesPerSecond)}/s", color = HiTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(downloadEta(download.etaSeconds), color = HiTextSecondary, fontSize = 11.sp)
            }
            when (download.status) {
                BrowserDownloadStatus.RUNNING, BrowserDownloadStatus.QUEUED -> IconButton(modifier = Modifier.size(48.dp).border(1.dp, HiSurfaceBorder, RoundedCornerShape(12.dp)), onClick = { if (download.ephemeral) onCancelGecko() else BrowserDownloads.pause(context, download.id) }) { Icon(if (download.ephemeral) Icons.Default.Close else Icons.Default.Pause, if (download.ephemeral) "Cancel download" else "Pause download", tint = HiTextPrimary) }
                BrowserDownloadStatus.PAUSED, BrowserDownloadStatus.FAILED, BrowserDownloadStatus.CANCELLED -> IconButton(modifier = Modifier.size(48.dp).border(1.dp, HiSurfaceBorder, RoundedCornerShape(12.dp)), onClick = { if (download.ephemeral) BrowserDownloads.removeFromList(context, download.id) else BrowserDownloads.retry(context, download.id) }) { Icon(if (download.ephemeral) Icons.Default.Delete else Icons.Default.PlayArrow, if (download.ephemeral) "Remove download" else "Resume or retry download", tint = HiTextPrimary) }
                BrowserDownloadStatus.COMPLETED -> IconButton(modifier = Modifier.size(48.dp).border(1.dp, HiSurfaceBorder, RoundedCornerShape(12.dp)), onClick = { if (download.isVideo) onPlay() else BrowserDownloads.open(context, download.id) }) { Icon(Icons.Default.PlayCircle, "Open downloaded file", tint = HiTextPrimary) }
            }
            Box(Modifier.padding(start = 6.dp)) {
                IconButton(modifier = Modifier.size(48.dp).border(1.dp, HiSurfaceBorder, RoundedCornerShape(12.dp)), onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${download.fileName}", tint = HiTextPrimary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(HiSurfaceElevated, RoundedCornerShape(12.dp))) {
                    if (download.isVideo && download.downloadedBytes > 0 && (download.status == BrowserDownloadStatus.COMPLETED || !download.ephemeral || (download.status == BrowserDownloadStatus.RUNNING && download.savedUri != null))) DropdownMenuItem(text = { Text(if (download.status == BrowserDownloadStatus.COMPLETED) "Play in Hi Player" else "Play while downloading", color = HiTextPrimary) }, onClick = { menu = false; onPlay() })
                    if (download.status == BrowserDownloadStatus.COMPLETED) {
                        DropdownMenuItem(text = { Text("Open with…", color = HiTextPrimary) }, onClick = { menu = false; BrowserDownloads.open(context, download.id) })
                        DropdownMenuItem(text = { Text("Share", color = HiTextPrimary) }, onClick = { menu = false; BrowserDownloads.share(context, download.id) })
                    }
                    DropdownMenuItem(text = { Text("Rename", color = HiTextPrimary) }, enabled = !download.finalizing, onClick = { menu = false; onRename() })
                    if (download.sourceUrl.isNotBlank()) DropdownMenuItem(text = { Text("Copy download link", color = HiTextPrimary) }, onClick = { menu = false; BrowserDownloads.copyLink(context, download.id) })
                    DropdownMenuItem(text = { Text("Details", color = HiTextPrimary) }, onClick = { menu = false; onDetails() })
                    if (download.isActive) DropdownMenuItem(text = { Text("Cancel download", color = HiTextPrimary) }, onClick = { menu = false; if (download.ephemeral) onCancelGecko() else BrowserDownloads.cancel(context, download.id) })
                    else DropdownMenuItem(text = { Text("Remove from history", color = HiTextPrimary) }, onClick = { menu = false; BrowserDownloads.removeFromList(context, download.id) })
                }
            }
        }
        if (download.isActive) {
            if (download.status == BrowserDownloadStatus.RUNNING && download.totalBytes <= 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = HiPrimaryCyan, trackColor = HiSurfaceBorder)
            else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp), color = HiPrimaryCyan, trackColor = HiSurfaceBorder)
        }
    }
}
