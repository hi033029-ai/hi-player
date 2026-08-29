package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsBottomSheet(
    onLoadSubtitleFile: () -> Unit,
    onFetchSubtitleUrl: () -> Unit,
    onDownloadCurrentSubtitle: () -> Unit,
    onOpenSubtitleCustomization: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenVideoSettings: () -> Unit,
    onOpenTelemetry: () -> Unit,
    onOpenRatingKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HiSurfaceDark,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Player Options & Utilities",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
            Text(
                text = "Subtitles, OMDb IMDb lookup key, audio delay & decoders",
                fontSize = 12.sp,
                color = HiTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle Options
            MoreOptionRow(
                icon = Icons.Default.Add,
                title = "Load subtitle file (.srt / .vtt)",
                subtitle = "Auto-converts SRT to WebVTT & loads track",
                onClick = {
                    onDismiss()
                    onLoadSubtitleFile()
                }
            )

            MoreOptionRow(
                icon = Icons.Default.Link,
                title = "Fetch subtitle from URL",
                subtitle = "HTTP / CORS-safe remote VTT/SRT fetcher",
                onClick = {
                    onDismiss()
                    onFetchSubtitleUrl()
                }
            )

            MoreOptionRow(
                icon = Icons.Default.Download,
                title = "Download current subtitle",
                subtitle = "Saves active VTT track to Downloads directory",
                onClick = {
                    onDismiss()
                    onDownloadCurrentSubtitle()
                }
            )

            MoreOptionRow(
                icon = Icons.Default.Palette,
                title = "Subtitle customization",
                subtitle = "Font size, family, colors, opacity & offset",
                onClick = {
                    onDismiss()
                    onOpenSubtitleCustomization()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Audio & Video & Telemetry
            MoreOptionRow(
                icon = Icons.Default.GraphicEq,
                title = "Audio Tracks & Lip-Sync Delay",
                subtitle = "Multi-language streams, loudness booster & delay",
                onClick = {
                    onDismiss()
                    onOpenAudioSettings()
                }
            )

            MoreOptionRow(
                icon = Icons.Default.Tune,
                title = "Video & Decoder Settings",
                subtitle = "HW decoding, aspect ratio & sleep timer",
                onClick = {
                    onDismiss()
                    onOpenVideoSettings()
                }
            )

            MoreOptionRow(
                icon = Icons.Default.Info,
                title = "Decoder Telemetry",
                subtitle = "FPS, resolution, codec & audio stream stats",
                onClick = {
                    onDismiss()
                    onOpenTelemetry()
                }
            )

            MoreOptionRow(
                icon = Icons.Default.Key,
                title = "TMDB Rating Key",
                subtitle = "Set your free TMDB API key to show star ratings",
                onClick = {
                    onDismiss()
                    onOpenRatingKey()
                }
            )
        }
    }
}

@Composable
private fun MoreOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HiSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = HiPrimaryCyan,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HiTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = HiTextSecondary
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}
