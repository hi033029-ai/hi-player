package com.example.ui

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Copy only the displayed video buffer before a same-item renderer handoff. */
internal suspend fun copyPictureFrame(view: SurfaceView, hdrTransfer: Int? = null): Bitmap? = withTimeoutOrNull(240L) {
    suspendCancellableCoroutine { result ->
        val surface = view.holder.surface
        if (!surface.isValid || view.width <= 0 || view.height <= 0) {
            result.resume(null)
            return@suspendCancellableCoroutine
        }
        val scale = minOf(1f, 1280f / maxOf(view.width, view.height))
        val width = (view.width * scale).roundToInt().coerceAtLeast(1)
        val height = (view.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = if (hdrTransfer != null && Build.VERSION.SDK_INT >= 26) {
            val colorSpace = when {
                Build.VERSION.SDK_INT >= 34 && hdrTransfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 ->
                    ColorSpace.Named.BT2020_PQ
                Build.VERSION.SDK_INT >= 34 && hdrTransfer == androidx.media3.common.C.COLOR_TRANSFER_HLG ->
                    ColorSpace.Named.BT2020_HLG
                else -> ColorSpace.Named.LINEAR_EXTENDED_SRGB
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.RGBA_F16, false, ColorSpace.get(colorSpace))
        } else Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, bitmap, { status ->
                if (result.isActive && status == PixelCopy.SUCCESS) {
                    result.resume(bitmap)
                } else {
                    bitmap.recycle()
                    if (result.isActive) result.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            if (result.isActive) result.resume(null)
        }
    }
}
