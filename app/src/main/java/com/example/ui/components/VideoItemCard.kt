package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoItem
import com.example.model.VideoResolutionBadge
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.HiCardShape
import com.example.ui.theme.HiThumbnailShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoItemCard(
    video: VideoItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    onFavoriteToggle: (Boolean) -> Unit,
    onPlayInBackground: () -> Unit,
    onPlayFromStart: () -> Unit = {},
    onShowDetails: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    progressFraction: Float = 0f,
    modifier: Modifier = Modifier,
    reorderHandle: (@Composable () -> Unit)? = null,
) {
    val palette = LocalHiPalette.current
    var menuExpanded by remember(video.id) { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compactScreen = maxWidth < 360.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HiCardShape)
                .hiContainerBorder(HiCardShape, selected = isSelected)
                .semantics { selected = isSelected }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(if (isSelected) palette.selectionSurface else palette.surface)
                .testTag("video_card_${video.id}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Thumbnail with Duration & 4K badge
                Box(
                    modifier = Modifier
                        .width(if (compactScreen) 96.dp else 116.dp)
                        .aspectRatio(1.45f)
                        .clip(HiThumbnailShape)
                        .background(palette.surface)
                ) {
                    VideoThumbnail(
                        video = video,
                        modifier = Modifier.fillMaxSize(),
                        shape = HiThumbnailShape,
                        contentScale = ContentScale.Crop
                    )

                    // Dark gradient overlay for badge readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0x99000000))
                                )
                            )
                    )

                    // Play icon in center
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center)
                            .background(Color(0x80000000), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 4K / HDR Badge
                    if (video.resolutionBadge == VideoResolutionBadge.UHD_4K || video.isHdr) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFFB300), Color(0xFFFF6D00))
                                    ),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = when {
                                    video.isHdr && video.resolutionBadge == VideoResolutionBadge.UHD_4K -> "4K HDR"
                                    video.isHdr -> "HDR"
                                    else -> "4K UHD"
                                },
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else if (video.resolutionBadge == VideoResolutionBadge.FHD_1080P) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .background(Color(0xCC0284C7), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "1080p",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (video.isNewVideo && !video.isPartialDownload) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .background(Color(0xFF16A34A), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("NEW", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // A partially downloaded file stays identifiable by its own
                    // filename. Do not add a transfer label, progress bar, speed,
                    // or state badge to the video library card.
                    if (!video.isPartialDownload) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = video.formattedDuration,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Video details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!video.isPartialDownload) {
                        if (progressFraction > 0f) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = palette.primary,
                                trackColor = palette.surfaceBorder,
                            )
                        }


                        Spacer(modifier = Modifier.height(3.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = video.formattedSize,
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                            Box(
                                modifier = Modifier
                                    .background(palette.selectionSurface, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = video.codec,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp,
                                    color = palette.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "Folder",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = video.folderName,
                                fontSize = 12.sp,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    reorderHandle?.invoke()

                    // Favorite Quick Icon
                    IconButton(
                        onClick = { onFavoriteToggle(video.isFavorite) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (video.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (video.isFavorite) "Remove from Favorites" else "Add to Favorites",
                            tint = if (video.isFavorite) Color(0xFFFF2A55) else palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Quick Actions Menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(palette.surfaceElevated)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Headphones, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                                },
                                text = { Text("Play in Background", color = palette.textPrimary, fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayInBackground()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Replay, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                                },
                                text = { Text("Play from start", color = palette.textPrimary, fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayFromStart()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        if (video.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (video.isFavorite) Color(0xFFFF2A55) else palette.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        if (video.isFavorite) "Remove from Favorites" else "Add to Favorites",
                                        color = palette.textPrimary,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onFavoriteToggle(video.isFavorite)
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp)) },
                                text = { Text("Rename", color = palette.textPrimary, fontSize = 13.sp) },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)) },
                                text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 13.sp) },
                                onClick = { menuExpanded = false; onDelete() }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                                },
                                text = { Text("Properties", color = palette.textPrimary, fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onShowDetails()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridCard(
    video: VideoItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    onFavoriteToggle: (Boolean) -> Unit,
    onPlayInBackground: () -> Unit,
    onPlayFromStart: () -> Unit = {},
    onShowDetails: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    progressFraction: Float = 0f,
    modifier: Modifier = Modifier,
    reorderHandle: (@Composable () -> Unit)? = null,
) {
    val palette = LocalHiPalette.current
    var menuExpanded by remember(video.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HiCardShape).hiContainerBorder(HiCardShape, selected = isSelected)
            .semantics { selected = isSelected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) palette.selectionSurface else palette.surface)
            .testTag("video_grid_card_${video.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Transparent)
            ) {
                VideoThumbnail(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                    contentScale = ContentScale.Crop
                )

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x99000000))
                            )
                        )
                )

                // Play icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                        .background(Color(0x80000000), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 4K / HDR Badge
                if (video.resolutionBadge == VideoResolutionBadge.UHD_4K || video.isHdr) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFB300), Color(0xFFFF6D00))
                                ),
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = when {
                                video.isHdr && video.resolutionBadge == VideoResolutionBadge.UHD_4K -> "4K HDR"
                                video.isHdr -> "HDR"
                                else -> "4K UHD"
                            },
                            color = Color.Black,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else if (video.resolutionBadge == VideoResolutionBadge.FHD_1080P) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color(0xCC0284C7), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "1080p",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (video.isNewVideo && !video.isPartialDownload) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color(0xFF16A34A), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("NEW", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Download state is intentionally not rendered in the library.
                // The title remains the only partial-download identifier here.
                if (!video.isPartialDownload) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Info below thumbnail
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (!video.isPartialDownload) {
                            Text(video.formattedSize, color = palette.textSecondary,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(video.codec, color = palette.textSecondary,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Box {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            reorderHandle?.invoke()
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(palette.surfaceElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play in Background", color = palette.textPrimary, fontSize = 12.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayInBackground()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Play from start", color = palette.textPrimary, fontSize = 12.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayFromStart()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (video.isFavorite) "Remove Favorite" else "Add to Favorite",
                                        color = palette.textPrimary,
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onFavoriteToggle(video.isFavorite)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename", color = palette.textPrimary, fontSize = 12.sp) },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 12.sp) },
                                onClick = { menuExpanded = false; onDelete() }
                            )
                            DropdownMenuItem(
                                text = { Text("Properties", color = palette.textPrimary, fontSize = 12.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onShowDetails()
                                }
                            )
                        }
                    }
                }

                if (!video.isPartialDownload) {
                    if (progressFraction > 0f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = palette.primary,
                            trackColor = palette.surfaceBorder,
                        )
                    }

                }
            }
        }
    }
}
