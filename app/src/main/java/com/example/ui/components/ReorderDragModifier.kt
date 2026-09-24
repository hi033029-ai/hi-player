package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Reorders only the displayed app list; it never moves files on device storage. */
fun Modifier.reorderableDrag(
    itemIndex: Int,
    itemCount: Int,
    thresholdPx: Float,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit = {},
): Modifier = pointerInput(itemIndex, itemCount, thresholdPx) {
    var accumulated = 0f
    var currentIndex = itemIndex
    detectDragGesturesAfterLongPress(
        onDrag = { change, dragAmount ->
            change.consume()
            accumulated += dragAmount.y
            while (abs(accumulated) >= thresholdPx) {
                val direction = if (accumulated > 0f) 1 else -1
                val nextIndex = (currentIndex + direction).coerceIn(0, itemCount - 1)
                if (nextIndex == currentIndex) {
                    accumulated = 0f
                    break
                }
                onMove(currentIndex, nextIndex)
                currentIndex = nextIndex
                accumulated -= direction * thresholdPx
            }
        },
        onDragEnd = onDrop,
        onDragCancel = onDrop,
    )
}
