package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoTrackInfo
import java.util.Locale

private val PickerTextPrimary = Color.White
private val PickerTextSecondary = Color(0xFFBDBDBD)
private val PickerAccent = Color(0xFF66B6FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsBottomSheet(
    subtitleTracks: List<VideoTrackInfo>,
    subtitleOffsetMs: Long,
    onSelectTrack: (VideoTrackInfo?) -> Unit,
    onSubtitleOffsetChange: (Long) -> Unit,
    onLoadExternalSubtitle: (Uri) -> Unit,
    onOpenCustomizeAppearance: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onLoadExternalSubtitle) }

    ModalBottomSheet(
        sheetMaxWidth = 560.dp,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF202020),
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PickerTextSecondary) }
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Subtitles", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PickerTextPrimary)
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                SubtitleTrackRow("Subtitles Off", subtitleTracks.none { it.isSelected }) { onSelectTrack(null) }
                subtitleTracks.forEach { track ->
                    SubtitleTrackRow(track.label, track.isSelected) { onSelectTrack(track) }
                }
            }
            if (subtitleTracks.isEmpty()) {
                Text("No embedded captions detected. Load a subtitle file below.",
                    color = PickerTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            Button(
                onClick = { subtitlePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("load_external_subtitle_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PickerTextPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Load subtitle file")
            }
            Button(
                onClick = onOpenCustomizeAppearance,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("customize_subtitle_appearance_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PickerTextPrimary)
            ) {
                Icon(Icons.Default.FormatColorText, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Subtitle appearance")
            }
            Text("Imported subtitle synchronization", color = PickerTextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text("Applies to imported SRT/VTT files, not embedded tracks.", color = PickerTextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { onSubtitleOffsetChange((subtitleOffsetMs - 100L).coerceAtLeast(-10_000L)) },
                    enabled = subtitleOffsetMs > -10_000L) {
                    Icon(Icons.Default.Remove, contentDescription = "Show imported subtitles earlier", tint = PickerTextPrimary)
                }
                Text(String.format(Locale.getDefault(), "%+.1f s", subtitleOffsetMs / 1000f),
                    color = PickerTextPrimary, modifier = Modifier.padding(horizontal = 12.dp))
                IconButton(onClick = { onSubtitleOffsetChange((subtitleOffsetMs + 100L).coerceAtMost(10_000L)) },
                    enabled = subtitleOffsetMs < 10_000L) {
                    Icon(Icons.Default.Add, contentDescription = "Show imported subtitles later", tint = PickerTextPrimary)
                }
            }
        }
    }
}

@Composable
private fun SubtitleTrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = PickerAccent, unselectedColor = PickerTextSecondary))
        Spacer(Modifier.width(12.dp))
        Text(label, color = PickerTextPrimary, fontSize = 14.sp)
    }
}
