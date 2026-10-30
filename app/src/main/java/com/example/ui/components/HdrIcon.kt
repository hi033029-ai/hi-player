package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Only this visible, bounded control can open HDR; the video surface has no HDR hotspot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HdrIcon(
    isActive: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(48.dp).combinedClickable(
            enabled = !isSwitching, role = Role.Button,
            onClickLabel = "HDR settings", onClick = onClick, onLongClick = onLongClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSwitching) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "HDR settings",
                tint = if (isActive) Color(0xFFFFC94A) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp))
        }
    }
}
