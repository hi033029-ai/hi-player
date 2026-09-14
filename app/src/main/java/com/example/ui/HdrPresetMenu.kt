package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.ColorPreset
import com.example.player.ColorPresets

@Composable
fun HdrPresetMenu(
    visible: Boolean,
    selected: ColorPreset,
    onSelect: (ColorPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        ColorPresets.ALL.forEach { preset ->
            Row(
                modifier = Modifier
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) {
                Text(
                    text = preset.name,
                    color = if (preset.name == selected.name) Color.Cyan else Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (preset.name == selected.name) "➤" else "",
                    color = Color.Cyan,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
