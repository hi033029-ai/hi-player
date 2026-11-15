package com.example.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import com.example.player.isHdrVideoFormat

internal fun shouldRequestMediaCodecHdrToneMapping(
    apiLevel: Int,
    displaySupportsHdr: Boolean,
    isHdrContent: Boolean,
): Boolean = apiLevel >= Build.VERSION_CODES.S && !displaySupportsHdr && isHdrContent

/**
 * Best-effort decoder-side HDR-to-SDR request for HDR video on displays that
 * advertise no HDR modes. HDR-capable displays keep the original decoder path.
 * Unsupported requests fall back to the ordinary MediaCodec configuration.
 */
@UnstableApi
internal class DisplayAwareHdrToneMappingAdapterFactory(
    context: Context,
) : MediaCodecAdapter.Factory {
    private val delegate = MediaCodecAdapter.Factory.getDefault(context)
    private val displaySupportsHdr = displaySupportsHdr(context)

    override fun createAdapter(
        configuration: MediaCodecAdapter.Configuration,
    ): MediaCodecAdapter {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return delegate.createAdapter(configuration)
        }
        val shouldRequestToneMapping = shouldRequestMediaCodecHdrToneMapping(
            apiLevel = Build.VERSION.SDK_INT,
            displaySupportsHdr = displaySupportsHdr,
            isHdrContent = isHdrVideoFormat(configuration.format),
        )
        if (!shouldRequestToneMapping) return delegate.createAdapter(configuration)

        val mediaFormat = configuration.mediaFormat
        val key = MediaFormat.KEY_COLOR_TRANSFER_REQUEST
        val hadPreviousRequest = mediaFormat.containsKey(key)
        val previousRequest = if (hadPreviousRequest) mediaFormat.getInteger(key) else 0
        mediaFormat.setInteger(key, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)

        return try {
            delegate.createAdapter(configuration)
        } catch (requestFailure: Exception) {
            if (hadPreviousRequest) {
                mediaFormat.setInteger(key, previousRequest)
            } else {
                mediaFormat.removeKey(key)
            }
            Log.w(TAG, "HDR-to-SDR decoder request failed; retrying normal configuration", requestFailure)
            delegate.createAdapter(configuration)
        }
    }

    private fun displaySupportsHdr(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return false
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?.hdrCapabilities
            ?.supportedHdrTypes
            ?.isNotEmpty() == true
    }

    private companion object {
        const val TAG = "HdrToneMapAdapter"
    }
}
