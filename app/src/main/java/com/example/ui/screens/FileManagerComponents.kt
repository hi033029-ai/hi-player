package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import com.example.model.FileType
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.ArchiveEntryInfo
import com.example.model.FileItem
import com.example.ui.components.FilePreviewThumbnail
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiCinemaBlack
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary
import com.example.ui.components.reorderableDrag
import com.example.viewmodel.FileManagerViewModel
import com.example.ui.components.HiPlayerHeader
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MenuActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: palette.textPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, fontSize = 14.sp, color = tint ?: palette.textPrimary)
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label.uppercase(Locale.ROOT), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, color = palette.textPrimary)
    }
}

@Composable
internal fun SelectionCheckCircle(isSelected: Boolean, modifier: Modifier = Modifier) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Box(
        modifier = modifier
            .size(22.dp)
            .testTag("file_selection_circle")
            .clip(CircleShape)
            .background(if (isSelected) palette.primary else Color.Transparent)
            .border(1.5.dp, if (isSelected) palette.primary else palette.surfaceBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = com.example.ui.theme.contrastContentColor(palette.primary, palette.surface),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveItemCard(
    archive: FileItem,
    onInspect: () -> Unit,
    onExtract: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showSelectionControl: Boolean = false,
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val ext = archive.extension.uppercase(Locale.ROOT)
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val dateStr = if (archive.lastModified > 0) dateFormat.format(Date(archive.lastModified)) else ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onInspect, onLongClick = onLongClick)
            .testTag("archive_card_${archive.name}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) palette.primary.copy(alpha = 0.16f) else Color.Transparent),
        border = if (isSelected) BorderStroke(1.dp, palette.primary) else null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSelectionControl) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Archive Type Badge Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.secondary.copy(alpha = 0.2f))
                    .border(1.dp, palette.secondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (archive.isApk) Icons.Default.Android else Icons.Default.FolderZip,
                        contentDescription = if (archive.isApk) "APK" else "Archive",
                        tint = if (archive.isApk) Color(0xFF34D399) else palette.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (archive.isApk) "APK" else ext.ifEmpty { "ZIP" },
                        color = if (archive.isApk) Color(0xFF34D399) else palette.secondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = archive.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = archive.formattedSize,
                        fontSize = 11.sp,
                        color = palette.secondary,
                        fontWeight = FontWeight.Medium
                    )
                    if (dateStr.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("•", fontSize = 10.sp, color = palette.textSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dateStr, fontSize = 10.sp, color = palette.textSecondary)
                    }
                }
            }

            // Quick Extract Button
            IconButton(
                onClick = onExtract,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("extract_archive_${archive.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Unarchive,
                    contentDescription = "Extract",
                    tint = palette.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItemCard(
    doc: FileItem,
    onOpen: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showSelectionControl: Boolean = false,
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val ext = doc.extension.uppercase(Locale.ROOT)
    val badgeColor = when (doc.extension) {
        "pdf" -> Color(0xFFEF4444)
        "doc", "docx" -> Color(0xFF3B82F6)
        "xls", "xlsx" -> Color(0xFF10B981)
        "ppt", "pptx" -> Color(0xFFF97316)
        "srt", "vtt", "ass" -> palette.primary
        else -> palette.textSecondary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .testTag("doc_card_${doc.name}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) palette.primary.copy(alpha = 0.16f) else Color.Transparent),
        border = if (isSelected) BorderStroke(1.dp, palette.primary) else null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSelectionControl) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.2f))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = ext.take(4),
                        color = badgeColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = doc.formattedSize,
                    fontSize = 11.sp,
                    color = badgeColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp)
            )

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItemCard(
    folder: FileItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    showSelectionControl: Boolean = false,
    isNew: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("folder_card_${folder.name}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) palette.primary.copy(alpha = 0.16f) else palette.surfaceElevated),
        border = if (isSelected) BorderStroke(1.dp, palette.primary) else null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSelectionControl) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                    .background(palette.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = folder.formattedSize,
                    fontSize = 11.sp,
                    color = palette.primary,
                    fontWeight = FontWeight.Normal
                )
            }

            if (isNew) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = "New files in folder",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenericFileItemCard(
    item: FileItem,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showSelectionControl: Boolean = false,
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val (icon, badgeBg, badgeTint, baseTypeLabel) = when {
        item.isApk -> Quadruple(Icons.Default.Android, Color(0xFF34D399).copy(alpha = 0.2f), Color(0xFF34D399), "APK")
        item.fileType == FileType.VIDEO -> Quadruple(Icons.Default.Movie, palette.primary.copy(alpha = 0.2f), palette.primary, "VIDEO")
        item.fileType == FileType.AUDIO -> Quadruple(Icons.Default.MusicNote, Color(0xFFC084FC).copy(alpha = 0.2f), Color(0xFFC084FC), "AUDIO")
        item.fileType == FileType.IMAGE -> Quadruple(Icons.Default.Image, Color(0xFF34D399).copy(alpha = 0.2f), Color(0xFF34D399), "IMAGE")
        else -> Quadruple(Icons.AutoMirrored.Filled.InsertDriveFile, palette.surfaceBorder, palette.textSecondary, item.extension.uppercase(Locale.ROOT))
    }
    val typeLabel = if (item.isPendingDownload) "DOWNLOADING" else baseTypeLabel

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) palette.primary.copy(alpha = 0.16f) else palette.surfaceElevated),
        border = if (isSelected) BorderStroke(1.dp, palette.primary) else null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSelectionControl) {
                SelectionCheckCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.width(8.dp))
            }
            FilePreviewThumbnail(
                item = item,
                fallbackIcon = icon,
                fallbackBackground = badgeBg,
                fallbackTint = badgeTint,
                modifier = Modifier.size(38.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        fontSize = 11.sp,
                        color = badgeTint,
                        fontWeight = FontWeight.Medium
                    )
                    if (typeLabel.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("•", fontSize = 10.sp, color = palette.textSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = typeLabel,
                            fontSize = 10.sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp)
            )

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

internal data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// -------------------------------------------------------------
// ARCHIVE INSPECTOR & EXTRACTOR BOTTOM SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveInspectorBottomSheet(
    archive: FileItem,
    entries: List<ArchiveEntryInfo>?,
    extractingProgress: FileManagerViewModel.ExtractionProgress?,
    onDismiss: () -> Unit,
    onExtractHere: () -> Unit,
    onChooseDestination: () -> Unit,
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(

        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceElevated,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                            .background(palette.secondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = archive.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${archive.formattedSize} • ${entries?.size ?: 0} entries",
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = palette.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Extraction status or action
            if (extractingProgress != null) {
                val remaining = (extractingProgress.total - extractingProgress.completed).coerceAtLeast(0)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Extracting ${extractingProgress.completed} of ${extractingProgress.total}",
                        color = palette.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Remaining: $remaining",
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                    extractingProgress.currentItemName?.let { name ->
                        Text(
                            text = name,
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (extractingProgress.total == 0) 0f
                            else extractingProgress.completed.toFloat() / extractingProgress.total
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)).hiContainerBorder(RoundedCornerShape(3.dp)),
                        color = palette.primary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onChooseDestination,
                        modifier = Modifier.weight(1f),
                    ) { Text("Choose folder", fontSize = 12.sp) }
                    Button(
                        onClick = onExtractHere,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primary,
                            contentColor = if (palette.isDark) Color.Black else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Unarchive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Extract Here", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = palette.surfaceBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Archive Contents",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = palette.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (entries == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.secondary, modifier = Modifier.size(32.dp))
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No file entries listed or non-zip format", color = palette.textSecondary, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDirectory) palette.secondary else palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.name,
                                fontSize = 12.sp,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = entry.formattedSize,
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDestinationPickerBottomSheet(
    archive: FileItem,
    destination: File,
    folders: List<FileItem>,
    atRoot: Boolean,
    onDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFolder: (FileItem) -> Unit,
    onExtractHere: () -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    ModalBottomSheet(
        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = 240.dp, max = 620.dp)
        ) {
            Text("Extract ${archive.name} to…", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!atRoot) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = palette.primary)
                    }
                }
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(destination.name.ifEmpty { "Internal Storage" }, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = palette.textSecondary.copy(alpha = 0.2f))
            if (folders.isEmpty()) {
                Text("No folders here. Extract directly into this location.", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(folder) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(folder.name, color = palette.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
            Button(
                onClick = onExtractHere,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = if (palette.isDark) Color.Black else Color.White)
            ) { Text("Extract Here", fontWeight = FontWeight.Bold) }
        }
    }
}

internal fun fileTypeIcon(item: FileItem): androidx.compose.ui.graphics.vector.ImageVector = when {
    item.isDirectory -> Icons.Default.Folder
    item.isApk -> Icons.Default.Android
    item.isArchive -> Icons.Default.FolderZip
    item.isDocument -> Icons.Default.Description
    item.isVideo -> Icons.Default.Movie
    item.isAudio -> Icons.Default.MusicNote
    item.extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> Icons.Default.Image
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

internal fun fileTypeLabel(item: FileItem): String = when {
    item.isDirectory -> "Folder"
    item.isApk -> "APK"
    item.isArchive -> "Archive"
    item.isDocument -> "Document"
    item.isVideo -> "Video"
    item.isAudio -> "Audio"
    item.extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> "Image"
    else -> "File"
}

@Composable
internal fun FileTypeBadge(item: FileItem) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(fileTypeIcon(item), contentDescription = fileTypeLabel(item), tint = palette.primary, modifier = Modifier.size(26.dp))
        Text(fileTypeLabel(item), color = palette.textSecondary, fontSize = 10.sp)
    }
}


@Composable
internal fun RecycleBinView(
    items: List<FileManagerViewModel.RecycleBinItem>,
    selectedIds: Set<String>,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onToggle: (String) -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    val selectedCount = selectedIds.size
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Card(
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Recycle Bin", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Deleted files stay here until you restore them or permanently delete them.",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSelectAll, enabled = items.isNotEmpty()) { Text("Select all") }
                    TextButton(onClick = onDeselectAll, enabled = selectedCount > 0) { Text("Deselect") }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (selectedCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("Restore to device") }
                TextButton(onClick = onDeletePermanently, modifier = Modifier.weight(1f)) {
                    Text("Delete permanently", color = Color(0xFFEF4444))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Recycle Bin is empty", color = palette.textSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.id }) { item ->
                    val selected = item.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(item.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) palette.primary.copy(alpha = 0.16f) else palette.surface
                        ),
                        border = if (selected) BorderStroke(1.dp, palette.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = palette.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Original: ${item.originalPath}", color = palette.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatRecycleBinSize(item.sizeBytes), color = palette.textSecondary, fontSize = 10.sp)
                            }
                            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = palette.primary)
                        }
                    }
                }
            }
        }
    }
}

internal fun formatRecycleBinSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** Stable folder-title slot; selection changes content, never the list viewport. */
@Composable
internal fun FileFolderHeader(
    currentDir: File,
    onNavigateUp: () -> Unit,
    selectionActions: (@Composable () -> Unit)? = null,
) {
    val palette = com.example.ui.theme.LocalHiPalette.current
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp).testTag("file_folder_header"), contentAlignment = Alignment.CenterStart) {
        if (selectionActions != null) selectionActions() else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateUp, enabled = currentDir.parentFile?.canRead() == true,
                    modifier = Modifier.testTag("navigate_up_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up", tint = palette.textPrimary)
                }
                Text(currentDir.name.ifEmpty { "Internal Storage" }, Modifier.weight(1f),
                    color = palette.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
