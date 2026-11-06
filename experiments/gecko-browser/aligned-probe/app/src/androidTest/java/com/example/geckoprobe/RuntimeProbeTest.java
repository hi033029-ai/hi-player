package com.example.geckoprobe;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@UnstableApi
@RunWith(AndroidJUnit4.class)
public class RuntimeProbeTest {
    @Test public void geckoLoadsLocalHtml() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        AtomicReference<String> title = new AtomicReference<>();
        AtomicReference<GeckoSession> sessionRef = new AtomicReference<>();
        try (ActivityScenario<ProbeActivity> activity = ActivityScenario.launch(ProbeActivity.class)) {
            activity.onActivity(a -> {
                GeckoRuntime runtime = GeckoRuntime.create(a.getApplicationContext());
                GeckoSession session = new GeckoSession();
                sessionRef.set(session);
                session.setContentDelegate(new GeckoSession.ContentDelegate() {
                    @Override public void onTitleChange(GeckoSession s, String value) { title.set(value); if ("Gecko runtime OK".equals(value)) loaded.countDown(); }
                });
                session.open(runtime);
                session.loadUri("data:text/html,%3Ctitle%3EGecko%20runtime%20OK%3C%2Ftitle%3E");
            });
            assertTrue("Gecko did not load the local test page", loaded.await(120, TimeUnit.SECONDS));
            assertEquals("Gecko runtime OK", title.get());
            activity.onActivity(a -> sessionRef.get().close());
        }
    }

    @Test public void matchingDav1dActuallyDecodesAv1() throws Exception {
        CountDownLatch frame = new CountDownLatch(1);
        AtomicReference<String> decoder = new AtomicReference<>();
        AtomicReference<PlaybackException> error = new AtomicReference<>();
        AtomicReference<ExoPlayer> playerRef = new AtomicReference<>();
        try (ActivityScenario<ProbeActivity> activity = ActivityScenario.launch(ProbeActivity.class)) {
            activity.onActivity(a -> {
                DefaultRenderersFactory factory = new DefaultRenderersFactory(a);
                factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
                ExoPlayer player = new ExoPlayer.Builder(a, factory).build();
                playerRef.set(player);
                player.addAnalyticsListener(new AnalyticsListener() {
                    @Override public void onVideoDecoderInitialized(EventTime t, String name, long initializationDurationMs) { decoder.set(name); }
                });
                player.addListener(new Player.Listener() {
                    @Override public void onRenderedFirstFrame() { frame.countDown(); }
                    @Override public void onPlayerError(PlaybackException e) { error.set(e); frame.countDown(); }
                });
                player.setVideoSurfaceView(a.surface);
                player.setMediaItem(MediaItem.fromUri(Uri.parse("asset:///test-av1.mp4")));
                player.prepare(); player.play();
            });
            assertTrue("AV1 did not produce a frame", frame.await(120, TimeUnit.SECONDS));
            assertNull("AV1 playback error", error.get());
            assertNotNull("Decoder name was not reported", decoder.get());
            assertTrue("Unexpected decoder: " + decoder.get(), decoder.get().toLowerCase().contains("dav1d"));
            activity.onActivity(a -> playerRef.get().release());
        }
    }
    @Test public void matchingFfmpegActuallyDecodesEac3() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> decoder = new AtomicReference<>();
        AtomicReference<PlaybackException> error = new AtomicReference<>();
        AtomicReference<ExoPlayer> playerRef = new AtomicReference<>();
        try (ActivityScenario<ProbeActivity> activity = ActivityScenario.launch(ProbeActivity.class)) {
            activity.onActivity(a -> {
                DefaultRenderersFactory factory = new DefaultRenderersFactory(a);
                factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
                ExoPlayer player = new ExoPlayer.Builder(a, factory).build();
                playerRef.set(player);
                player.addAnalyticsListener(new AnalyticsListener() {
                    @Override public void onAudioDecoderInitialized(EventTime t, String name, long initializationDurationMs) { decoder.set(name); }
                });
                player.addListener(new Player.Listener() {
                    @Override public void onPlaybackStateChanged(int state) { if (state == Player.STATE_ENDED) done.countDown(); }
                    @Override public void onPlayerError(PlaybackException e) { error.set(e); done.countDown(); }
                });
                player.setMediaItem(MediaItem.fromUri(Uri.parse("asset:///test-eac3.mp4")));
                player.prepare(); player.play();
            });
            assertTrue("E-AC-3 did not finish", done.await(120, TimeUnit.SECONDS));
            assertNull("E-AC-3 playback error", error.get());
            assertNotNull("Audio decoder name was not reported", decoder.get());
            assertTrue("Unexpected audio decoder: " + decoder.get(), decoder.get().toLowerCase().contains("ffmpeg"));
            activity.onActivity(a -> playerRef.get().release());
        }
    }

}
