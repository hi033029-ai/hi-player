package com.example.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.VideoAspectRatioMode

/**
 * Long-press-on-video menu: a vertical stack of aspect-ratio options anchored to the
 * left edge — matching the reference app's behavior, not a horizontal row and not
 * centered. Place this as a sibling overlay aligned to Alignment.CenterStart inside
 * the same Box as the video (see PlayerScreen.kt) so it sits over the left edge of
 * the screen regardless of video size/position.
 */
@Composable
fun AspectRatioPickerOverlay(
    visible: Boolean,
    current: VideoAspectRatioMode,
    onSelect: (VideoAspectRatioMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(vertical = 8.dp),
        ) {
            VideoAspectRatioMode.entries.forEach { mode ->
                val selected = mode == current
                Text(
                    text = mode.label,
                    color = if (selected) Color.Cyan else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}
