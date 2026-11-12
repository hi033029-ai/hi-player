package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.ColorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(UnstableApi::class)
class HdrFormatTest {
    @Test fun classifiesSignalledFormatsWithoutGuessingFromResolution() {
        val pq = ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build()
        val hlg = ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_HLG).build()
        assertEquals(HdrFormat.DOLBY_VISION, HdrFormat.classify("video/dolby-vision", null, null))
        assertEquals(HdrFormat.DOLBY_VISION, HdrFormat.classify("video/hevc", "dvhe.05.06", null))
        assertEquals(HdrFormat.HDR10, HdrFormat.classify("video/hevc", "hvc1", pq))
        assertEquals(HdrFormat.HDR10_PLUS, HdrFormat.classify("video/hevc", "hdr10plus", pq))
        assertEquals(HdrFormat.HLG, HdrFormat.classify("video/hevc", null, hlg))
        assertEquals(HdrFormat.SDR, HdrFormat.classify("video/hevc", null, null))
    }
}
