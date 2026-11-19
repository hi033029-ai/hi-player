package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val HiCardShape = RoundedCornerShape(18.dp)
val HiThumbnailShape = RoundedCornerShape(12.dp)

val HiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = HiThumbnailShape,
    medium = HiCardShape,
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
