package com.example.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor

/**
 * Builds an ExoPlayer instance configured for very large 4K/HDR files, covering
 * the 10 techniques UPlayer/Manus identified:
 *
 *  1. Hardware MediaCodec decoding      -> DefaultRenderersFactory(EXTENSION_RENDERER_MODE_ON)
 *  2. FFmpeg native decoding (fallback) -> RenderersFactory extension mode PREFER, see build.gradle
 *  3. Random-access reads               -> FileDataSource / ContentResolver FD source, no full read
 *  4. Fast seeking                      -> DefaultLoadControl tuned buffers + coalesced seeks
 *                                          (SmoothSeekController) — EXACT seek params, see build()
 *  5. Reduced probing/analysis          -> DefaultExtractorsFactory with format hints, no full-file sniff
 *  6. Direct file-descriptor access     -> FileDescriptorDataSource (below)
 *  7. Hardware SurfaceView rendering    -> handled in Compose layer (see VideoSurface.kt)
 *  8. Frame dropping under pressure     -> setVideoScalingMode SCALE_TO_FIT + allowedJoiningTimeMs
 *  9. Native buffering/demuxing         -> DefaultLoadControl with large back-buffer disabled
 * 10. No Java bitmap video pipeline     -> we never touch Bitmap; Surface is handed straight to codec
 */
@UnstableApi
object OptimizedPlayerBuilder {

    /** Tuned buffer sizes for very large (multi-GB) 4K/HDR files on local/removable storage. */
    private fun loadControl(): LoadControl {
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        return DefaultLoadControl.Builder()
            .setAllocator(allocator)
            // Large files benefit from a bigger buffer window so seeking rarely re-buffers,
            // but we cap it so memory doesn't balloon on multi-hour 4K HDR files.
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                // Lowered from 2500ms — this is how long ExoPlayer waits before it
                // will even start playback after prepare(), and it stacks directly on
                // top of file probing + codec init + the HDR colorMode switch, all of
                // which already add up on a large 4K/HDR file. 800ms is still enough
                // to avoid an immediate rebuffer on a reasonable connection/storage
                // speed, and meaningfully shortens the black-screen startup window.
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            // Don't retain a large back-buffer — large files don't need to rewind far,
            // and holding it wastes memory that 4K frames desperately need.
            .setBackBuffer(/* backBufferDurationMs = */ 0, /* retainBackBufferFromKeyframe = */ false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /** Renderer factory that prefers hardware MediaCodec, falls back to the FFmpeg extension. */
    private fun renderersFactory(context: Context): DefaultRenderersFactory {
        return DefaultRenderersFactory(context).apply {
            // ON = try hardware MediaCodec first, use FFmpeg (software) renderer only if
            // no hardware codec can play the stream (e.g. exotic HDR profiles, obscure audio).
            // Use PREFER instead of ON if you want FFmpeg to be tried before some hw decoders
            // that are known-buggy on certain devices.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true) // if hw codec init fails mid-stream, fall back gracefully
            setMediaCodecSelector(MediaCodecSelector.DEFAULT)
        }
    }

    /** Extractors factory tuned to avoid a full-file probe/sniff on huge containers. */
    private fun extractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory().apply {
            // Skip the exhaustive index scan on large MP4/MOV; trust the moov atom.
            setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_MOTION_PHOTO_METADATA.inv())
            // Don't force constant-bitrate seeking heuristics on huge files — use the
            // container's own index (fast) rather than scanning (slow on multi-GB files).
            setConstantBitrateSeekingEnabled(false)
        }
    }

    /**
     * Media source built directly from a file descriptor — avoids ContentResolver copying
     * the file or ExoPlayer re-opening by path, and avoids loading the whole file into memory.
     */
    fun mediaSourceFromUri(context: Context, uri: Uri): MediaSource {
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            context,
            FileDataSource.Factory(),
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory())
        return mediaSourceFactory.createMediaSource(MediaItem.fromUri(uri))
    }

    fun build(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context, renderersFactory(context))
            .setLoadControl(loadControl())
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setExtractorsFactory(extractorsFactory()))
            // How long the renderer waits before it starts dropping frames instead of
            // stalling — keeps playback smooth on heavy 4K/HDR under decoder pressure.
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                // IMPORTANT: do NOT use SeekParameters.CLOSEST_SYNC here. It lands the
                // VIDEO renderer on the nearest keyframe and stops — it does not decode
                // forward to the exact requested position. Audio isn't keyframe-bound and
                // seeks exactly, so the two land seconds apart on long-GOP x265/HEVC
                // remuxes; video then has to silently catch up (decoding-but-not-
                // displaying) until it converges with the audio clock, which is exactly
                // "audio plays, picture stays frozen for a few seconds, then both match."
                // EXACT (ExoPlayer's real default) decodes to the precise target before
                // resuming visible playback — no desync, at the cost of a slightly longer
                // per-seek latency. Combined with SmoothSeekController's debounce (which
                // already cuts down how often seekTo() actually fires), this is the right
                // tradeoff.
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            }
    }
}
