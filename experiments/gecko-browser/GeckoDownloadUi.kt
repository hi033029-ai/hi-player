package com.example.browser.gecko

import android.net.Uri
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.downloads.DownloadFileResolver
import com.example.ui.theme.LocalHiPalette

/** WebView-style options, backed by the already approved Gecko response stream. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeckoDownloadUi(controller: GeckoDownloadController, onPlay: ((String, String?, String?) -> Unit)? = null) {
    val context = LocalContext.current
    val state = controller.state
    val palette = LocalHiPalette.current
    var fileName by remember(state) { mutableStateOf((state as? GeckoDownloadState.Offer)?.name.orEmpty()) }
    val isVideo = state is GeckoDownloadState.Offer && (state.mimeType?.startsWith("video/") == true || fileName.substringAfterLast('.').lowercase() in setOf("mp4", "mkv", "webm", "mov", "avi", "ts", "m4v", "3gp"))
    var folderMenu by remember(state) { mutableStateOf(false) }
    var requestedFor by remember { mutableStateOf<GeckoDownloadState.Offer?>(null) }
    var selectedTree by remember(state) { mutableStateOf<Uri?>(null) }
    var pickerFailed by remember(state) { mutableStateOf(false) }
    var permissionFailed by remember(state) { mutableStateOf(false) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val expected = requestedFor
        requestedFor = null
        if (expected != null && controller.state === expected && uri?.scheme == "content") selectedTree = uri
    }
    when (state) {
        is GeckoDownloadState.Offer -> ModalBottomSheet(
            modifier = Modifier.testTag("gecko-download-offer"),
            onDismissRequest = controller::reject,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.background,
        ) {
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Download file?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (!state.recordInHistory) Text(
                    "The file stays on your device, but will not appear in browser download history.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("gecko-download-private-notice"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).background(palette.primary.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (isVideo) Icons.Default.Movie else Icons.Default.InsertDriveFile, null, tint = palette.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(value = fileName, onValueChange = { fileName = it.take(240) }, singleLine = true,
                        label = { Text("File name") }, trailingIcon = { Icon(Icons.Default.Edit, "Edit file name") },
                        modifier = Modifier.weight(1f).testTag("gecko-download-name"), isError = fileName.isBlank())
                }
                Text(state.length?.let { Formatter.formatFileSize(context, it) } ?: "Size unknown", style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(onClick = { folderMenu = true }) {
                        Icon(Icons.Default.FolderOpen, null)
                        Text(if (selectedTree != null) "Selected folder" else "Download (default)")
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                        if (Build.VERSION.SDK_INT >= 29) DropdownMenuItem(text = { Text("Download (default)") }, onClick = { selectedTree = null; folderMenu = false })
                        DropdownMenuItem(text = { Text("Choose another folder…") }, onClick = {
                            folderMenu = false; requestedFor = state
                            runCatching { chooseFolder.launch(selectedTree) }.onFailure { requestedFor = null; pickerFailed = true }
                        })
                    }
                }
                if (pickerFailed) Text("Could not open the folder picker.")
                if (permissionFailed) Text("Could not retain access to this folder. Choose another folder.")
                HorizontalDivider(color = palette.surfaceBorder)
                if (isVideo && state.playUrl != null && onPlay != null) {
                    TextButton(onClick = { val url = state.playUrl; controller.reject(); onPlay(url, state.mimeType, DownloadFileResolver.safeName(fileName)) }) {
                        Icon(Icons.Default.PlayArrow, null); Text("Play now in Hi Player")
                    }
                    Text("Playback uses the media address only. Cookie-protected and DRM media may need to stay on the website.", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(modifier = Modifier.fillMaxWidth().testTag("gecko-download-confirm"), enabled = fileName.isNotBlank() && (Build.VERSION.SDK_INT >= 29 || selectedTree != null), onClick = {
                    val tree = selectedTree
                    val granted = tree == null || !state.recordInHistory || runCatching {
                        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }.isSuccess
                    if (!granted) permissionFailed = true else {
                        val name = DownloadFileResolver.safeName(fileName)
                        val target = tree?.let { uri -> { GeckoDocumentTreeDestination(context, uri, name, state.mimeType) } }
                        controller.approve(name, if (tree == null) "Downloads" else "selected folder", target)
                    }
                }) { Icon(Icons.Default.Download, null); Text("Download") }
                TextButton(onClick = controller::reject, modifier = Modifier.fillMaxWidth().testTag("gecko-download-cancel")) { Text("Cancel") }
                Text("This authenticated stream cannot resume after cancellation or browser exit.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
            }
        }
        is GeckoDownloadState.Active -> Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(state.name, maxLines = 1)
                    val finalizing = state.length != null && state.length > 0 && state.bytes >= state.length
                    Text(state.length?.takeIf { it > 0 }?.let {
                        "${if (finalizing) "Saving file" else "Downloading"} · ${Formatter.formatFileSize(context, state.bytes)} of ${Formatter.formatFileSize(context, it)}"
                    } ?: "Downloading · ${Formatter.formatFileSize(context, state.bytes)} received")
                    Text(if (state.speedBytesPerSecond > 0) "${Formatter.formatFileSize(context, state.speedBytesPerSecond)}/s" else "Measuring download speed…")
                    if (state.length != null && state.length > 0) LinearProgressIndicator(progress = { (state.bytes.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                TextButton(onClick = controller::cancel) { Text("Cancel") }
            }
        }
        is GeckoDownloadState.Finished -> AlertDialog(
            onDismissRequest = controller::dismissResult,
            title = { Text(when (state.outcome) {
                is GeckoDownloadOutcome.Complete -> "Download complete"
                GeckoDownloadOutcome.Cancelled -> "Download cancelled"
                GeckoDownloadOutcome.Failed -> "Download failed"
            }) },
            text = { Text(if (state.outcome is GeckoDownloadOutcome.Complete) if (state.recorded == false) "The file was saved in ${state.location}, but could not be added to download history." else "The file was saved in ${state.location}." else "The download could not be completed.") },
            confirmButton = { TextButton(onClick = controller::dismissResult) { Text("OK") } },
        )
        GeckoDownloadState.Idle -> Unit
    }
}
