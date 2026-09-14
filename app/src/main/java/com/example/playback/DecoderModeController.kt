package com.example.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/**
 * Three decoder strategies, exposed in the top-right 3-dot menu.
 *
 *  HW      - hardware MediaCodec only. Fastest / most power-efficient. If a device
 *            can't decode a given stream in hardware, playback fails instead of
 *            falling back — use this to force-test pure hardware behavior.
 *  HW_PLUS - hardware first, falls back to the FFmpeg software decoder only when
 *            hardware can't handle the stream (e.g. unusual HDR/audio profiles).
 *            This is the recommended default — matches EXTENSION_RENDERER_MODE_ON.
 *  SW      - FFmpeg software decode preferred over hardware. Useful for files with
 *            broken/edge-case streams where the hardware decoder misbehaves, or for
 *            comparing quality/output between hw and sw paths. Higher battery/CPU cost.
 */
enum class DecoderMode(val label: String, val extensionMode: Int) {
    HW("Hardware (HW)", DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF),
    HW_PLUS("Hardware+ (HW+)", DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
    SW("Software (SW)", DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
}

/**
 * Rebuilds the ExoPlayer instance with a new decoder mode while preserving the
 * current media item, playback position, and play/pause state — so switching
 * SW/HW/HW+ from the 3-dot menu resumes exactly where the user was.
 */
@UnstableApi
class DecoderModeController(
    private val context: Context,
    initialMode: DecoderMode = DecoderMode.HW_PLUS,
) {
    var mode: DecoderMode = initialMode
        private set

    private var _player: ExoPlayer = buildPlayer(mode)
    val player: ExoPlayer get() = _player

    private fun buildPlayer(mode: DecoderMode): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(mode.extensionMode)
            setEnableDecoderFallback(true)
        }
        return ExoPlayer.Builder(context, renderersFactory).build()
    }

    /**
     * Switches decoder mode. Returns the new ExoPlayer instance — callers must
     * reattach it to their PlayerView / VideoSurface, since ExoPlayer's renderer
     * set can't be swapped on a live instance and must be rebuilt.
     */
    fun switchMode(newMode: DecoderMode, currentUri: Uri?): ExoPlayer {
        if (newMode == mode) return _player

        val positionMs = _player.currentPosition
        val wasPlaying = _player.isPlaying
        val uri = currentUri ?: _player.currentMediaItem?.localConfiguration?.uri

        _player.release()

        mode = newMode
        _player = buildPlayer(newMode)

        if (uri != null) {
            val mediaSource = OptimizedPlayerBuilder.mediaSourceFromUri(context, uri)
            _player.setMediaSource(mediaSource, positionMs)
            _player.prepare()
            _player.playWhenReady = wasPlaying
        }

        return _player
    }

    fun release() {
        _player.release()
    }
}
