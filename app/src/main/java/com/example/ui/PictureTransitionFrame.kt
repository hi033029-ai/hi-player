package com.example.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Copy only the displayed video buffer before a same-item renderer handoff. */
internal suspend fun copyPictureFrame(view: SurfaceView): Bitmap? = withTimeoutOrNull(240L) {
    suspendCancellableCoroutine { result ->
        val surface = view.holder.surface
        if (!surface.isValid || view.width <= 0 || view.height <= 0) {
            result.resume(null)
            return@suspendCancellableCoroutine
        }
        val scale = minOf(1f, 1280f / maxOf(view.width, view.height))
        val bitmap = Bitmap.createBitmap(
            (view.width * scale).roundToInt().coerceAtLeast(1),
            (view.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
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
