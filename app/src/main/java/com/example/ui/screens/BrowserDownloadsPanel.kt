package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.downloads.*
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.hiContainerBorder

@Composable
internal fun BrowserDownloadsPanel(downloads: List<BrowserDownload>, query: String, onPlay: (Long) -> Unit) =
    BrowserDownloadsPanel(downloads, query, onCancelGecko = {}, onPauseGecko = {}, onResumeGecko = {}, onPlay = onPlay)

@Composable
internal fun BrowserDownloadsPanel(
    downloads: List<BrowserDownload>,
    query: String,
    onCancelGecko: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onPauseGecko: (Long) -> Unit = {},
    onResumeGecko: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    var rename by remember { mutableStateOf<BrowserDownload?>(null) }
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf<BrowserDownload?>(null) }
    val shown = downloads.filter { query.isBlank() || it.fileName.contains(query, true) }.sortedByDescending { it.id }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        if (shown.isEmpty()) {
            Text(
                if (downloads.isEmpty()) "Your downloads will appear here." else "No matching downloads",
                color = palette.textSecondary,
                modifier = Modifier.padding(18.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { download ->
                    DownloadProgressRow(
                        download = download,
                        onPlay = { onPlay(download.id) },
                        onCancelGecko = { onCancelGecko(download.id) },
                        onPauseGecko = { onPauseGecko(download.id) },
                        onResumeGecko = { onResumeGecko(download.id) },
                        onRename = { rename = download; name = download.fileName },
                        onDetails = { details = download },
                    )
                }
            }
        }
    }

    rename?.let { download ->
        AlertDialog(
            modifier = Modifier.hiContainerBorder(RoundedCornerShape(28.dp)),
            onDismissRequest = { rename = null },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.surfaceBorder,
                    ),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    BrowserDownloads.rename(context, download.id, name)
                    rename = null
                }) { Text("Save", color = palette.primary) }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel", color = palette.textSecondary) } },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
        )
    }

    details?.let { original ->
        val download = downloads.firstOrNull { it.id == original.id } ?: original
        AlertDialog(
            modifier = Modifier.hiContainerBorder(RoundedCornerShape(28.dp)),
            onDismissRequest = { details = null },
            title = { Text("Download details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(download.fileName, color = palette.textPrimary)
                    Text("Status: ${download.status.name.lowercase()}")
                    Text("Saved to: ${download.saveLocation}")
                    Text("${downloadBytes(download.downloadedBytes)} / ${if (download.totalBytes > 0) downloadBytes(download.totalBytes) else "unknown size"}")
                    Text("Type: ${download.mimeType ?: "Detecting"}")
                    if (download.sourceUrl.isNotBlank()) {
                        Text("Server: ${runCatching { java.net.URI(download.sourceUrl).host }.getOrDefault("")}")
                    }
                    download.error?.let { Text(it) }
                    if (download.status != BrowserDownloadStatus.COMPLETED && download.isVideo) {
                        Text(
                            if (download.ephemeral) "Play becomes available after this browser stream finishes saving."
                            else "Play while downloading depends on the container. Some MP4 files need the end of the file first."
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Done", color = palette.primary) } },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
        )
    }
}

@Composable
private fun DownloadProgressRow(
    download: BrowserDownload,
    onPlay: () -> Unit,
    onCancelGecko: () -> Unit,
    onPauseGecko: () -> Unit,
    onResumeGecko: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val cardShape = RoundedCornerShape(18.dp)
    var menu by remember(download.id) { mutableStateOf(false) }
    val fraction = if (download.totalBytes > 0) {
        (download.downloadedBytes.toDouble() / download.totalBytes).toFloat().coerceIn(0f, 1f)
    } else 0f
    val cancelDownload = {
        if (download.ephemeral) onCancelGecko() else BrowserDownloads.cancel(context, download.id)
    }
    val pauseDownload = {
        if (download.ephemeral) onPauseGecko() else BrowserDownloads.pause(context, download.id)
    }
    val resumeDownload = {
        if (download.ephemeral) onResumeGecko() else BrowserDownloads.retry(context, download.id)
    }

    Column(
        Modifier.fillMaxWidth()
            .testTag("download-card-${download.id}")
            .background(palette.surface, cardShape)
            .border(1.dp, palette.surfaceBorder, cardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().testTag("download-main-row-${download.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(palette.primary.copy(alpha = .12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (download.isVideo) Icons.Default.Movie else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = palette.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                    download.fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${downloadBytes(download.downloadedBytes)}${if (download.totalBytes > 0) " / ${downloadBytes(download.totalBytes)}" else ""}",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                )
                val running = download.status == BrowserDownloadStatus.RUNNING && !download.finalizing
                Text(
                    when {
                        download.finalizing -> "Saving to ${download.saveLocation}…"
                        running -> download.error ?: if (download.totalBytes > 0) "Downloading · ${(fraction * 100).toInt()}%" else "Downloading"
                        download.status == BrowserDownloadStatus.COMPLETED -> download.saveLocation
                        else -> download.error ?: download.status.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                )
                if (running) {
                    Text(
                        "${downloadBytes(download.speedBytesPerSecond)}/s · ${downloadEta(download.etaSeconds)} left",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }

            when (download.status) {
                BrowserDownloadStatus.RUNNING, BrowserDownloadStatus.QUEUED -> {
                    if (!download.finalizing) {
                        DownloadActionButton(Icons.Default.Pause, "Pause download", palette.surfaceBorder, palette.textPrimary, pauseDownload)
                    }
                    DownloadActionButton(Icons.Default.Close, "Cancel download", palette.surfaceBorder, palette.textPrimary, cancelDownload)
                }
                BrowserDownloadStatus.PAUSED -> {
                    DownloadActionButton(Icons.Default.PlayArrow, "Resume download", palette.surfaceBorder, palette.textPrimary, resumeDownload)
                    DownloadActionButton(Icons.Default.Close, "Cancel download", palette.surfaceBorder, palette.textPrimary, cancelDownload)
                }
                BrowserDownloadStatus.FAILED, BrowserDownloadStatus.CANCELLED -> {
                    DownloadActionButton(
                        if (download.ephemeral) Icons.Default.Delete else Icons.Default.PlayArrow,
                        if (download.ephemeral) "Remove download" else "Retry download",
                        palette.surfaceBorder,
                        palette.textPrimary,
                    ) {
                        if (download.ephemeral) BrowserDownloads.removeFromList(context, download.id)
                        else BrowserDownloads.retry(context, download.id)
                    }
                }
                BrowserDownloadStatus.COMPLETED -> DownloadActionButton(
                    Icons.Default.PlayCircle,
                    "Open downloaded file",
                    palette.surfaceBorder,
                    palette.textPrimary,
                ) {
                    if (download.isVideo) onPlay() else BrowserDownloads.open(context, download.id)
                }
            }

            Box(Modifier.padding(start = 4.dp)) {
                DownloadActionButton(Icons.Default.MoreVert, "Options for ${download.fileName}", palette.surfaceBorder, palette.textPrimary) {
                    menu = true
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.background(palette.surfaceElevated, RoundedCornerShape(12.dp)),
                ) {
                    if (download.isVideo && download.downloadedBytes > 0 &&
                        (download.status == BrowserDownloadStatus.COMPLETED || !download.ephemeral)
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (download.status == BrowserDownloadStatus.COMPLETED) "Play in Hi Player" else "Play while downloading", color = palette.textPrimary) },
                            onClick = { menu = false; onPlay() },
                        )
                    }
                    if (download.status == BrowserDownloadStatus.COMPLETED) {
                        DropdownMenuItem(text = { Text("Open with…", color = palette.textPrimary) }, onClick = { menu = false; BrowserDownloads.open(context, download.id) })
                        DropdownMenuItem(text = { Text("Share", color = palette.textPrimary) }, onClick = { menu = false; BrowserDownloads.share(context, download.id) })
                    }
                    DropdownMenuItem(text = { Text("Rename", color = palette.textPrimary) }, enabled = !download.finalizing, onClick = { menu = false; onRename() })
                    if (download.sourceUrl.isNotBlank()) {
                        DropdownMenuItem(text = { Text("Copy download link", color = palette.textPrimary) }, onClick = { menu = false; BrowserDownloads.copyLink(context, download.id) })
                    }
                    DropdownMenuItem(text = { Text("Details", color = palette.textPrimary) }, onClick = { menu = false; onDetails() })
                    if (download.isActive) {
                        DropdownMenuItem(text = { Text("Cancel download", color = palette.textPrimary) }, onClick = { menu = false; cancelDownload() })
                    } else {
                        DropdownMenuItem(text = { Text("Remove from history", color = palette.textPrimary) }, onClick = { menu = false; BrowserDownloads.removeFromList(context, download.id) })
                    }
                }
            }
        }

        if (download.isActive) {
            if (download.status == BrowserDownloadStatus.RUNNING && download.totalBytes <= 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = palette.primary,
                    trackColor = palette.surfaceBorder,
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = palette.primary,
                    trackColor = palette.surfaceBorder,
                )
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    borderColor: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier.size(44.dp).border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        onClick = onClick,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}
