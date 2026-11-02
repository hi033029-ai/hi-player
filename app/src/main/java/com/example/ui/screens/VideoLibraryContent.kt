package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.AppThemeMode
import com.example.filemanager.StoragePermission
import com.example.model.VideoItem
import com.example.ui.components.FolderItemRow
import com.example.ui.components.FolderGridCard
import com.example.ui.components.HorizontalThemeSelector
import com.example.ui.components.VideoGridCard
import com.example.ui.components.VideoInfoDialog
import com.example.ui.components.VideoItemCard
import com.example.ui.components.VideoThumbnail
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.LocalHiUiMetrics
import com.example.ui.components.HiPlayerHeader
import com.example.viewmodel.LibraryNavMode
import com.example.viewmodel.LibraryViewMode
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.VideoSortOption
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

internal fun formatVideoBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format("%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format("%.2f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

@Composable
internal fun PlanSheetChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    palette: com.example.ui.theme.HiThemePalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) palette.primary else palette.surfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) palette.primary else palette.surfaceBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    if (palette.isDark) Color.Black else Color.White
                } else palette.textSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) {
                    if (palette.isDark) Color.Black else Color.White
                } else palette.textPrimary
            )
        }
    }
}

@Composable
internal fun EmptyState(onBrowse: () -> Unit, onRefresh: () -> Unit = {}) {
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current
    val emptyContext = androidx.compose.ui.platform.LocalContext.current
    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasVideoAccess by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, videoPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasSelectedVideoAccess by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 34 && androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val accessOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(accessOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasVideoAccess = androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, videoPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                hasSelectedVideoAccess = Build.VERSION.SDK_INT >= 34 && androidx.core.content.ContextCompat.checkSelfPermission(emptyContext, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        accessOwner.lifecycle.addObserver(observer)
        onDispose { accessOwner.lifecycle.removeObserver(observer) }
    }
    val videoAccessLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasVideoAccess = grants[videoPermission] == true
        hasSelectedVideoAccess = Build.VERSION.SDK_INT >= 34 && grants[android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        if (hasVideoAccess || hasSelectedVideoAccess) onRefresh()
        else runCatching {
            emptyContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", emptyContext.packageName, null)))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(palette.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasVideoAccess) "No videos found" else if (hasSelectedVideoAccess) "Only selected videos are available" else "Allow access to your videos",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasVideoAccess) "Tap below to pick a video file from your device storage." else if (hasSelectedVideoAccess) "Allow all videos or select more videos to show them in the library." else "Hi Player needs permission to show the videos on your phone.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = { if (hasVideoAccess) onBrowse() else videoAccessLauncher.launch(if (Build.VERSION.SDK_INT >= 34) arrayOf(videoPermission, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else arrayOf(videoPermission)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = if (palette.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (hasVideoAccess) "Browse Storage" else "Allow access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: LibraryViewModel.ContinueWatchingItem,
    onClick: () -> Unit,
    onRemove: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current

    Card(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
            .testTag("continue_watching_item_${item.video.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Thumbnail with Play Icon overlay and Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).hiContainerBorder(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(palette.surface)
            ) {
                VideoThumbnail(
                    video = item.video,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    contentScale = ContentScale.Crop
                )

                // Dark gradient shade overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x99000000))
                            )
                        )
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Remove from Continue Watching", tint = Color.White)
                }

                // Play Button in Center
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(palette.primary.copy(alpha = 0.9f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = if (palette.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Progress Bar at Bottom of Thumbnail
                if (item.progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.BottomCenter),
                        color = palette.primary,
                        trackColor = Color(0x66FFFFFF)
                    )
                }
            }

            // Info text
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = item.video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.progressText,
                    fontSize = 9.5.sp,
                    color = palette.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


@Composable
fun ContinueWatchingStrip(
    items: List<LibraryViewModel.ContinueWatchingItem>,
    onVideoSelected: (VideoItem) -> Unit,
    onRemove: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current
    val uiMetrics = LocalHiUiMetrics.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONTINUE WATCHING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${items.size} active",
                fontSize = 10.sp,
                color = palette.textSecondary
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items, key = { it.video.uri.toString() }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onVideoSelected(item.video) },
                    onRemove = { onRemove(item.video.uri.toString()) }
                )
            }
        }
    }
}
