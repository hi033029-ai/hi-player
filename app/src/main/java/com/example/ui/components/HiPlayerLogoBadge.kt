package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * The logo artwork is a 1920 px image in the default (mdpi) drawable folder, so
 * every painterResource() call decoded it again, scaled up to the screen density
 * (over 5000 px square on most phones), on the main thread. This cache decodes
 * one downsampled copy once and shares it everywhere the logo appears.
 */
object HiPlayerLogo {
    @Volatile private var cached: ImageBitmap? = null

    fun bitmap(context: Context): ImageBitmap {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.hi_player_logo, options)
                ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            decoded.prepareToDraw()
            return decoded.asImageBitmap().also { cached = it }
        }
    }

    /** Warms the cache off the main thread while the app starts. */
    fun preload(context: Context) {
        val appContext = context.applicationContext
        Thread({ runCatching { bitmap(appContext) } }, "hi-logo-preload").start()
    }
}

@Composable
fun rememberHiPlayerLogoPainter(): Painter {
    val context = LocalContext.current
    return remember { BitmapPainter(HiPlayerLogo.bitmap(context), filterQuality = FilterQuality.Medium) }
}

/**
 * Modern Custom Shaped Logo Badge for Hi Player
 * Features a modern Squircle/Stadium shape with dynamic double-ring gradient border.
 */
@Composable
fun HiPlayerLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(percent = 38), // Continuous smooth Squircle
    backgroundColor: Color = Color(0xFFFFFFFF),
    borderColor: Color = Color(0xFF0056B3)
) {
    val outerGlowBrush = Brush.linearGradient(
        colors = listOf(
            borderColor,
            Color(0xFF00E5FF),
            borderColor.copy(alpha = 0.6f)
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = borderColor.copy(alpha = 0.3f),
                spotColor = borderColor.copy(alpha = 0.4f)
            )
            .clip(shape)
            .background(outerGlowBrush)
            .padding(2.dp) // Outer border width
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberHiPlayerLogoPainter(),
            contentDescription = "Hi Player Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size - 4.dp)
                .clip(shape)
        )
    }
}
