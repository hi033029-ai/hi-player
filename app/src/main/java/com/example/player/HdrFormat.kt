package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.util.Locale

/** Detected dynamic-range family. Labels describe only what the stream signals. */
@OptIn(UnstableApi::class)
enum class HdrFormat(val label: String) {
    SDR("SDR / unspecified"),
    HDR10("HDR10 (PQ)"),
    HDR10_PLUS("HDR10+ (PQ)"),
    HLG("HLG"),
    DOLBY_VISION("Dolby Vision"),
    ;

    val isHdr: Boolean get() = this != SDR

    companion object {
        fun of(format: Format?): HdrFormat = if (format == null) SDR else
            classify(format.sampleMimeType, format.codecs, format.colorInfo)

        /**
         * Dolby Vision profile 5 often carries no HDR transfer in [ColorInfo]; detect it from the
         * MIME type or codec string so it keeps the native HDR output path instead of SDR GLES.
         * HDR10+ is reported only when the codec string declares it; Media3 does not expose
         * per-frame dynamic metadata, so ordinary PQ streams are reported as HDR10.
         */
        fun classify(sampleMimeType: String?, codecs: String?, colorInfo: ColorInfo?): HdrFormat {
            val codec = codecs?.lowercase(Locale.ROOT).orEmpty()
            val dolbyVision = sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION ||
                codec.split(',').any { it.trim().let { c -> c.startsWith("dvh1") || c.startsWith("dvhe") || c.startsWith("dvav") || c.startsWith("dva1") || c.startsWith("dav1") } }
            if (dolbyVision) return DOLBY_VISION
            return when (colorInfo?.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> if ("hdr10plus" in codec || "hdr10+" in codec) HDR10_PLUS else HDR10
                C.COLOR_TRANSFER_HLG -> HLG
                else -> SDR
            }
        }
    }
}
