package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import com.example.model.VideoTrackInfo

private val PickerSurface = Color.Black
private val PickerSurfaceElevated = Color(0xFF202020)
private val PickerSurfaceSelected = Color(0xFF383838)
private val PickerTextPrimary = Color.White
private val PickerTextSecondary = Color(0xFFBDBDBD)

@Composable
fun AudioSettingsBottomSheet(
    audioTracks: List<VideoTrackInfo>,
    volumeBoostPercent: Int,
    audioDelayMs: Long,
    onSelectTrack: (VideoTrackInfo) -> Unit,
    onVolumeBoostChange: (Int) -> Unit,
    onAudioDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .widthIn(max = 220.dp)
            .heightIn(max = 205.dp)
            .clip(RoundedCornerShape(4.dp)).hiContainerBorder(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.94f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 2.dp)
        ) {
            Text(
                text = "Audio Language",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PickerTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))

            // AUDIO LANGUAGE LIST - this is the primary purpose of the sheet,
            // so it leads and is no longer buried below decorative controls.
            if (audioTracks.isEmpty()) {
                Text(
                    text = "Default Stereo Stream (Internal)",
                    fontSize = 13.sp,
                    color = PickerTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    audioTracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = track.isSupported) { onSelectTrack(track) }
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(26.dp)
                                    .background(if (track.isSelected) PickerTextPrimary else PickerSurfaceElevated)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = audioTrackTitle(track),
                                    fontSize = 12.sp,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        track.isSelected -> PickerTextPrimary
                                        !track.isSupported -> PickerTextSecondary.copy(alpha = 0.65f)
                                        else -> PickerTextPrimary
                                    }
                                )
                                Text(
                                    text = audioTrackChannelSuffix(track),
                                    fontSize = 9.sp,
                                    color = PickerTextPrimary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                if (!track.isSupported) {
                                    Text(
                                        text = "— unsupported on this device",
                                        fontSize = 9.sp,
                                        color = PickerTextSecondary.copy(alpha = 0.72f),
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }
                            if (track.isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = PickerTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

        }
    }
}

private fun audioTrackTitle(track: VideoTrackInfo): String {
    val label = track.label.trim()
    if (label.isNotEmpty() && !label.equals("und", ignoreCase = true) && !label.equals("unknown", ignoreCase = true)) {
        return label
    }
    return when (track.language?.lowercase()) {
        "hi", "hin" -> "Hindi"
        "en", "eng" -> "English"
        "ta", "tam" -> "Tamil"
        "te", "tel" -> "Telugu"
        "ml", "mal" -> "Malayalam"
        "kn", "kan" -> "Kannada"
        "bn", "ben" -> "Bengali"
        else -> "Audio track"
    }
}

private fun audioTrackChannelSuffix(track: VideoTrackInfo): String {
    return when (track.channelCount) {
        1 -> "Mono · 1.0"
        2 -> "Stereo · 2.0"
        6 -> "Surround · 5.1"
        8 -> "Surround · 7.1"
        in 3..5 -> "${track.channelCount} channels"
        else -> "Audio stream"
    }
}
