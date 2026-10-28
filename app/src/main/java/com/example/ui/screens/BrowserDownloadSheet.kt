package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.downloads.*
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserDownloadSheet(
    request: BrowserDownloadRequest,
    onDismiss: () -> Unit,
    onDownload: (BrowserDownloadRequest, String, Boolean, String?, String) -> Unit,
    onPlay: (BrowserDownloadRequest) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    var inspected by remember(request) { mutableStateOf(request) }
    var fileName by remember(request) { mutableStateOf(request.fileName) }
    var edited by remember(request) { mutableStateOf(false) }
    var treeUri by remember(request) { mutableStateOf<String?>(null) }
    var destination by remember(request) { mutableStateOf("Download") }
    var folderMenu by remember { mutableStateOf(false) }
    var checking by remember(request) { mutableStateOf(true) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val folder = DocumentFile.fromTreeUri(context, uri)
            check(folder?.canWrite() == true)
            treeUri = uri.toString(); destination = folder?.name ?: "Selected folder"
        }.onFailure { Toast.makeText(context, "This folder is not writable. Choose another folder.", Toast.LENGTH_LONG).show() }
    }
    val submit = { onDownload(inspected, DownloadFileResolver.safeName(fileName), edited, treeUri, destination) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) submit() else Toast.makeText(context, "Choose a folder to save the file without storage permission", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(request.url) {
        val cookie = runCatching { CookieManager.getInstance().getCookie(request.url).orEmpty() }.getOrDefault("")
        val preview = withContext(Dispatchers.IO) { runCatching { DownloadLinkProbe.inspect(request, cookie) }.getOrDefault(request) }
        inspected = preview
        if (!edited) fileName = preview.fileName
        checking = false
    }
    val isVideo = inspected.mimeType?.startsWith("video/") == true || fileName.substringAfterLast('.').lowercase() in setOf("mp4", "mkv", "webm", "mov", "avi", "ts", "m4v", "3gp")
    ModalBottomSheet(
        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.textTertiary) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).background(palette.primary.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (isVideo) Icons.Default.Movie else Icons.Default.InsertDriveFile, null, tint = palette.primary)
                }
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(
                    value = fileName, onValueChange = { fileName = it; edited = true }, singleLine = true,
                    label = { Text("File name") }, trailingIcon = { Icon(Icons.Default.Edit, "Edit file name") },
                    modifier = Modifier.weight(1f), isError = fileName.isBlank(),
                    supportingText = { if (fileName.isBlank()) Text("Enter a file name") },
                )
            }
            Box(Modifier.padding(start = 90.dp, end = 22.dp, top = 4.dp, bottom = 18.dp)) {
                OutlinedButton(onClick = { folderMenu = true }) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp)); Text(destination, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                    DropdownMenuItem(text = { Text("Download (default)") }, onClick = { treeUri = null; destination = "Download"; folderMenu = false })
                    DropdownMenuItem(text = { Text("Choose another folder…") }, onClick = {
                        folderMenu = false
                        runCatching { chooseFolder.launch(treeUri?.let(Uri::parse)) }.onFailure { Toast.makeText(context, "No folder picker is available", Toast.LENGTH_SHORT).show() }
                    })
                }
            }
            HorizontalDivider(color = palette.surfaceBorder)
            if (isVideo) DownloadSheetAction(Icons.Default.PlayArrow, "Play now", "Open in Hi Player without downloading", true) { onPlay(inspected.copy(fileName = fileName)) }
            DownloadSheetAction(Icons.Default.Download, "Download", if (inspected.totalBytes > 0) downloadBytes(inspected.totalBytes) else if (checking) "Checking file size…" else "Size unknown", fileName.isNotBlank()) {
                if (Build.VERSION.SDK_INT < 29 && treeUri == null && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else submit()
            }
            DownloadSheetAction(Icons.Default.Close, "Cancel", null, true, onDismiss)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DownloadSheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String?, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalHiPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, if (title == "Download") palette.primary else palette.surfaceBorder, RoundedCornerShape(16.dp)).background(if (title == "Download") palette.primary.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Transparent).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (enabled) palette.textPrimary else palette.textTertiary, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(25.dp))
        Column {
            Text(title, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            detail?.let { Text(it, color = palette.textSecondary, fontSize = 12.sp) }
        }
    }
}

internal fun downloadBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
internal fun downloadEta(seconds: Long?): String = when {
    seconds == null -> "Time unknown"
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds}s left"
}
