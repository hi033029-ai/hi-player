package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.HiCardShape
import com.example.ui.theme.HiThumbnailShape
import com.example.viewmodel.VideoFolder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItemRow(
    folder: VideoFolder,
    isSelected: Boolean = false,
    hasNewVideo: Boolean = folder.hasNewVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val palette = LocalHiPalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(HiCardShape).hiContainerBorder(HiCardShape, selected = isSelected)
            .background(if (isSelected) palette.selectionSurface else palette.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("folder_item_${folder.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(palette.selectionSurface, HiThumbnailShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Folder Name and Video Count
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = folder.name,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            folder.location?.let {
                Text(it, fontSize = 12.sp, color = palette.textSecondary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val countText = if (folder.videoCount == 1) "1 video" else "${folder.videoCount} videos"
                Text(
                    text = countText,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    fontWeight = FontWeight.Normal
                )
                if (hasNewVideo) {
                    Text(
                        text = "New",
                        modifier = Modifier
                            .background(palette.selectionSurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.primary
                    )
                }
            }
        }
    }
}

/**
 * Grid-shaped folder tile, used when the library's grid/list toggle is set
 * to grid mode (previously the toggle had no effect at all on folder listings).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridCard(
    name: String,
    videoCount: Int,
    isSelected: Boolean = false,
    hasNewVideo: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    location: String? = null,
) {
    val palette = LocalHiPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HiCardShape).hiContainerBorder(HiCardShape, selected = isSelected)
            .background(if (isSelected) palette.selectionSurface else palette.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { selected = isSelected }
            .padding(14.dp)
            .testTag("folder_grid_$name"),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(palette.selectionSurface, HiThumbnailShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = name,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        location?.let {
            Text(it, fontSize = 12.sp, color = palette.textSecondary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }

        Spacer(modifier = Modifier.height(2.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (videoCount == 1) "1 video" else "$videoCount videos",
                fontSize = 12.sp,
                color = palette.textSecondary
            )
            if (hasNewVideo) {
                Text(
                    text = "New",
                    modifier = Modifier
                        .background(palette.selectionSurface, RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.primary
                )
            }
        }
    }
}
