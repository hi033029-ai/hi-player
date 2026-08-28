package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.VideoItem
import com.example.model.VideoResolutionBadge
import com.example.ui.theme.LocalHiPalette

@Composable
fun VideoItemCard(
    video: VideoItem,
    onClick: () -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
    onPlayInBackground: () -> Unit,
    onShowDetails: () -> Unit,
    progressFraction: Float = 0f,
    index: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_card_${video.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plan sheet index number (if supplied)
            if (index != null) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .padding(start = 2.dp, end = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%02d", index),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textTertiary
                    )
                }
            }

            // Video Thumbnail with Duration & 4K badge
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
            ) {
                VideoThumbnail(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
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
                            text = if (video.isHdr) "4K HDR" else "4K UHD",
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

                // Duration badge
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

                // Watch Progress Bar
                if (progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.BottomCenter),
                        color = palette.primary,
                        trackColor = Color(0x66FFFFFF)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Video Details (Plan sheet columns)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = video.formattedSize,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                    Box(
                        modifier = Modifier
                            .background(palette.primary.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = video.codec,
                            fontSize = 10.sp,
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
                        fontSize = 10.5.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Favorite Quick Icon
            IconButton(
                onClick = { onFavoriteToggle(video.isFavorite) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (video.isFavorite) Color(0xFFFF2A55) else palette.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick Actions Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
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
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        },
                        text = { Text("Media Technical Details", color = palette.textPrimary, fontSize = 13.sp) },
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

@Composable
fun VideoGridCard(
    video: VideoItem,
    onClick: () -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
    onPlayInBackground: () -> Unit,
    onShowDetails: () -> Unit,
    progressFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_grid_card_${video.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(palette.surface)
            ) {
                VideoThumbnail(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
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
                            text = if (video.isHdr) "4K HDR" else "4K UHD",
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

                // Duration badge
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

                // Watch Progress
                if (progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.BottomCenter),
                        color = palette.primary,
                        trackColor = Color(0x66FFFFFF)
                    )
                }
            }

            // Info below thumbnail
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
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
                                text = { Text("Media Technical Details", color = palette.textPrimary, fontSize = 12.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onShowDetails()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.formattedSize,
                        fontSize = 10.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = video.codec,
                        fontSize = 10.sp,
                        color = palette.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
