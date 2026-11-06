package com.example.geckoprobe;
import android.content.Context;
import android.os.Handler;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.av1.Libgav1VideoRenderer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.ArrayList;
/** Deliberately unsafe reproduction: this bridge crashes with the bundled JNI on Media3 1.11. */
@UnstableApi
public final class LegacyGav1Factory extends DefaultRenderersFactory {
    public LegacyGav1Factory(Context c) { super(c); }
    @Override protected void buildVideoRenderers(Context context, int mode, MediaCodecSelector selector, boolean fallback, Handler handler, VideoRendererEventListener listener, long joiningTime, ArrayList<Renderer> out) {
        int start = out.size();
        super.buildVideoRenderers(context, mode, selector, fallback, handler, listener, joiningTime, out);
        if (mode != EXTENSION_RENDERER_MODE_OFF) out.add(mode == EXTENSION_RENDERER_MODE_PREFER ? start : out.size(), new Libgav1VideoRenderer(joiningTime, handler, listener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
    }
}
