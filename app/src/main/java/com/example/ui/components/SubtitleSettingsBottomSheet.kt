package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoTrackInfo
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsBottomSheet(
    subtitleTracks: List<VideoTrackInfo>,
    subtitleOffsetMs: Long,
    onSelectTrack: (VideoTrackInfo?) -> Unit,
    onSubtitleOffsetChange: (Long) -> Unit,
    onLoadExternalSubtitle: (android.net.Uri) -> Unit,
    onOpenCustomizeAppearance: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onLoadExternalSubtitle(it) }
    }

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
                text = "Subtitle & Closed Caption Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
            Text(
                text = "Embedded PGS, ASS, SRT tracks & external subtitle sync",
                fontSize = 12.sp,
                color = HiTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Load External Subtitle (.srt / .vtt)
            Button(
                onClick = { subtitlePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("load_external_subtitle_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HiSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = HiPrimaryCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Load External Subtitle (.srt / .ass / .vtt)",
                    color = HiPrimaryCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Customize Appearance - opens the existing subtitle style sheet
            // (size/color/opacity/position). That customization already
            // worked, it just had no visible entry point from here before.
            Button(
                onClick = onOpenCustomizeAppearance,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("customize_subtitle_appearance_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HiSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.FormatColorText,
                    contentDescription = null,
                    tint = HiPrimaryCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Customize Appearance (Size, Color, Opacity)",
                    color = HiPrimaryCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SUBTITLE SYNC / OFFSET
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HiSurfaceElevated)
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = HiPrimaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subtitle Sync Offset",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HiTextPrimary
                            )
                        }
                        Text(
                            text = if (subtitleOffsetMs >= 0) "+${subtitleOffsetMs}ms" else "${subtitleOffsetMs}ms",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HiPrimaryCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSubtitleOffsetChange(subtitleOffsetMs - 250) }) {
                            Text("-250ms", color = HiPrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onSubtitleOffsetChange(0) }) {
                            Text("Reset", color = HiTextSecondary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onSubtitleOffsetChange(subtitleOffsetMs + 250) }) {
                            Text("+250ms", color = HiPrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SUBTITLE TRACKS LIST
            Text(
                text = "Available Subtitle Tracks",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Option 1: Disable Subtitles
            val anySelected = subtitleTracks.any { it.isSelected }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!anySelected) Color(0x3300E5FF) else HiSurfaceElevated)
                    .clickable { onSelectTrack(null) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaptionDisabled,
                        contentDescription = null,
                        tint = if (!anySelected) HiPrimaryCyan else HiTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Subtitles Off",
                        fontSize = 13.sp,
                        fontWeight = if (!anySelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (!anySelected) HiPrimaryCyan else HiTextPrimary
                    )
                }
                if (!anySelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = HiPrimaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(subtitleTracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (track.isSelected) Color(0x3300E5FF) else HiSurfaceElevated)
                            .clickable { onSelectTrack(track) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = null,
                                tint = if (track.isSelected) HiPrimaryCyan else HiTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = track.label,
                                fontSize = 13.sp,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (track.isSelected) HiPrimaryCyan else HiTextPrimary
                            )
                        }
                        if (track.isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = HiPrimaryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
