package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatioMode

@Composable
fun AspectRatioPickerOverlay(
    visible: Boolean,
    current: AspectRatioMode,
    onSelect: (AspectRatioMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier.zIndex(10f)) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(vertical = 8.dp),
        ) {
            AspectRatioMode.entries.forEach { mode ->
                Text(
                    text = mode.displayName,
                    color = if (mode == current) Color.Cyan else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}
