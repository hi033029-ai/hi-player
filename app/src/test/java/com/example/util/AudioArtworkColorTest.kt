package com.example.util

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.example.ui.theme.MonochromeDarkPalette
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioArtworkColorTest {
    @Test fun greenArtworkProducesDarkGreenPlayerBackgroundAndReadablePalette() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.rgb(12, 180, 60)) }
        val palette = AudioArtworkColor.palette(MonochromeDarkPalette, bitmap)

        assertTrue(palette != null)
        assertTrue(palette!!.isDark)
        assertTrue(palette.background.green > palette.background.red)
        assertTrue(palette.background.green > palette.background.blue)
        assertTrue(palette.background.green < 0.25f)
        assertTrue(palette.textPrimary.red > palette.background.red)
    }

    @Test fun grayscaleArtworkUsesTheAppThemeInsteadOfInventingAHue() {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.GRAY) }
        assertNull(AudioArtworkColor.palette(MonochromeDarkPalette, bitmap))
    }
}
