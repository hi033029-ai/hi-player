package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.VideoFolder

@Composable
fun FolderItemRow(
    folder: VideoFolder,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("folder_item_${folder.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Soft rounded Folder Icon matching reference image
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (palette.isDark) Color(0xFF232B38)
                    else Color(0xFFE2E8F0)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = folder.name,
                tint = if (isSelected) palette.primary
                else if (palette.isDark) Color(0xFF94A3B8)
                else Color(0xFF94A3B8),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(18.dp))

        // Folder Name and Video Count
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = folder.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = if (folder.name.equals("Download", ignoreCase = true) || isSelected) {
                    palette.primary
                } else {
                    palette.textPrimary
                },
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            val countText = if (folder.videoCount == 1) "1 video" else "${folder.videoCount} videos"
            Text(
                text = countText,
                fontSize = 13.sp,
                color = palette.textSecondary,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * Grid-shaped folder tile, used when the library's grid/list toggle is set
 * to grid mode (previously the toggle had no effect at all on folder listings).
 */
@Composable
fun FolderGridCard(
    name: String,
    videoCount: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (palette.isDark) Color(0xFF181E29) else Color(0xFFF1F5F9))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 10.dp)
            .testTag("folder_grid_$name"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (palette.isDark) Color(0xFF232B38)
                    else Color(0xFFE2E8F0)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = name,
                tint = if (isSelected) palette.primary else Color(0xFF94A3B8),
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) palette.primary else palette.textPrimary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (videoCount == 1) "1 video" else "$videoCount videos",
            fontSize = 11.5.sp,
            color = palette.textSecondary
        )
    }
}
