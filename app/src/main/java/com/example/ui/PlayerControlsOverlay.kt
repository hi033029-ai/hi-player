package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.DecoderMode

// Matches the reference screenshot exactly:
// - Title row: filename + 3-dot menu (new, top right)
// - Secondary row: icon + text label below each button, left and right groups
// - Seek bar with timestamps
// - Bottom row: icon + text label below each button, three groups (left/center/right)

private val ControlBg = Color(0xFF1A1A2E).copy(alpha = 0.85f)
private val ActiveCyan = Color(0xFF00D4FF)
private val LabelColor = Color.White

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerControlsOverlay(
    title: String,
    isPlaying: Boolean,
    currentDecoderMode: DecoderMode,
    elapsedLabel: String,
    remainingLabel: String,
    seekProgress: Float,
    isHdrActive: Boolean,
    isHdrSwitching: Boolean,
    speedLabel: String,
    onDecoderModeSelected: (DecoderMode) -> Unit,
    onRotate: () -> Unit,
    onPictureInPicture: () -> Unit,
    onToggleHdr: () -> Unit,
    onSpeed: () -> Unit,
    onPlayAsAudio: () -> Unit,
    onEqualizer: () -> Unit,
    onTrackSettings: () -> Unit,
    onLock: () -> Unit,
    onAspectRatioTap: () -> Unit,       // single tap — cycles fit → crop → stretch → ...
    onAspectRatioLongPress: () -> Unit, // long press — opens the full vertical list
    aspectLabel: String,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onCaptions: () -> Unit,
    onAudioLanguage: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
    ) {
        // ── Title row ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = LabelColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DecoderModeMenu(
                current = currentDecoderMode,
                onSelected = onDecoderModeSelected,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Secondary row: left group + spacer + right group ──────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ControlBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEFT — Rotate / Floating Window / HDR
            ControlButton(icon = Icons.Filled.ScreenRotation, label = "Rotate Screen", onClick = onRotate)
            ControlButton(icon = Icons.Filled.PictureInPictureAlt, label = "Floating Window", onClick = onPictureInPicture)

            // HDR gets its own composable for the animated glow + guard
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isHdrSwitching) { onToggleHdr() }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                HdrIcon(
                    isActive = isHdrActive,
                    isSwitching = isHdrSwitching,
                    onClick = onToggleHdr,
                )
                Text(
                    "HDR",
                    color = if (isHdrActive) ActiveCyan else LabelColor,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // RIGHT — Speed / Play as Audio / Equalizer / Tune
            ControlButton(
                icon = Icons.Filled.Speed,
                label = speedLabel,  // shows current speed e.g. "Speed 1.0X"
                onClick = onSpeed,
                labelColor = LabelColor,
            )
            ControlButton(icon = Icons.Filled.Headphones, label = "Play as Audio", onClick = onPlayAsAudio)
            ControlButton(icon = Icons.Filled.GraphicEq, label = "Equalizer", onClick = onEqualizer)
            ControlButton(icon = Icons.Filled.Tune, label = "", onClick = onTrackSettings)
        }

        // ── Seek bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ControlBg)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(elapsedLabel, color = LabelColor, fontSize = 11.sp)
            Slider(
                value = seekProgress,
                onValueChange = onSeek,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(
                    thumbColor = ActiveCyan,
                    activeTrackColor = ActiveCyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Text(remainingLabel, color = LabelColor, fontSize = 11.sp)
        }

        // ── Bottom row: Lock+Aspect | Prev/Play/Next | CC+AudioLang ──────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ControlBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEFT cluster
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlButton(icon = Icons.Filled.Lock, label = "Lock", onClick = onLock)

                // Dedicated combinedClickable here: single tap cycles the mode directly,
                // long press opens the full vertical list — both gestures on one widget.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = onAspectRatioTap,
                            onLongClick = onAspectRatioLongPress,
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AspectRatio,
                        contentDescription = null,
                        tint = LabelColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = aspectLabel,
                        color = LabelColor,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // CENTER cluster — Previous / Stop / Next
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkipButton(icon = Icons.Filled.SkipPrevious, onClick = onPrevious)
                    Text("Previous", color = LabelColor, fontSize = 10.sp)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    PlayPauseButton(isPlaying = isPlaying, onClick = onPlayPause)
                    Text(
                        text = if (isPlaying) "Stop" else "Play",
                        color = ActiveCyan,
                        fontSize = 10.sp,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkipButton(icon = Icons.Filled.SkipNext, onClick = onNext)
                    Text("Next", color = LabelColor, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // RIGHT cluster
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlButton(icon = Icons.Filled.ClosedCaption, label = "CC", onClick = onCaptions)
                ControlButton(icon = Icons.Filled.MusicNote, label = "Audio language", onClick = onAudioLanguage)
            }
        }
    }
}

/** Icon on top, text label below — the unit used for every control in the reference layout. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = LabelColor,
    iconTint: Color = LabelColor,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = labelColor,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Top-right 3-dot menu — decoder mode only, not shown in reference but requested. */
@Composable
private fun DecoderModeMenu(
    current: DecoderMode,
    onSelected: (DecoderMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = LabelColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DecoderMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label)
                            if (mode == current) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = { onSelected(mode); expanded = false },
                )
            }
        }
    }
}
