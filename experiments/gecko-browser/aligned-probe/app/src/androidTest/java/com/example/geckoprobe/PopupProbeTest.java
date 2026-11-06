package com.example.geckoprobe;

import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Isolated real-Gecko event-order check, not a production browser test. */
@RunWith(AndroidJUnit4.class)
public class PopupProbeTest {
    @Test public void userActivatedBlankPopupReportsLoadBeforeNewSession() throws Exception {
        CountDownLatch ready = new CountDownLatch(1), clicked = new CountDownLatch(1), popup = new CountDownLatch(1);
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<GeckoSession> parent = new AtomicReference<>(), child = new AtomicReference<>();
        AtomicReference<int[]> position = new AtomicReference<>();
        AtomicReference<GeckoView> viewRef = new AtomicReference<>();
        try (ActivityScenario<ProbeActivity> activity = ActivityScenario.launch(ProbeActivity.class)) {
            activity.onActivity(a -> {
                GeckoView view = new GeckoView(a);
                viewRef.set(view);
                a.setContentView(view);
                GeckoRuntime runtime = GeckoRuntime.create(a.getApplicationContext());
                GeckoSession session = new GeckoSession();
                parent.set(session);
                session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
                    @Override public void onPageStop(GeckoSession s, boolean success) { if (success) ready.countDown(); }
                });
                session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                    @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest request) {
                        if (request.target == TARGET_WINDOW_NEW) events.add("request:" + request.uri + ":" + request.hasUserGesture);
                        return GeckoResult.allow();
                    }
                    @Override public void onLocationChange(GeckoSession s, String url, java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms, Boolean gesture) {
                        if (url != null && url.endsWith("#clicked")) clicked.countDown();
                    }
                    @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                        events.add("session:" + uri);
                        GeckoSession result = new GeckoSession();
                        child.set(result);
                        popup.countDown();
                        return GeckoResult.fromValue(result);
                    }
                });
                session.open(runtime);
                view.setSession(session);
                String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0;width:100%;height:100%}button{width:100%;height:100%;font-size:30px}</style></head><body><button onclick=\"window.location.hash='clicked';window.open('about:blank')\">Open</button></body></html>";
                session.loadUri("data:text/html," + Uri.encode(html));
            });
            assertTrue("Local page did not finish loading", ready.await(120, TimeUnit.SECONDS));
            // Page stop precedes the first composited frame on slow software emulators.
            SystemClock.sleep(30_000);
            activity.onActivity(a -> {
                GeckoView view = viewRef.get();
                if (view.isShown() && view.getWidth() > 0 && view.getHeight() > 0) {
                    int[] p = new int[2]; view.getLocationOnScreen(p);
                    position.set(new int[]{p[0] + view.getWidth()/2, p[1] + view.getHeight()/2});
                }
            });
            assertNotNull("View was not visible and laid out", position.get());
            int[] p = position.get(); long t = SystemClock.uptimeMillis();
            android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, p[0], p[1], 0);
            MotionEvent up = MotionEvent.obtain(t, t + 100, MotionEvent.ACTION_UP, p[0], p[1], 0);
            down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try {
                assertTrue("Could not inject touch down", automation.injectInputEvent(down, true));
                assertTrue("Could not inject touch up", automation.injectInputEvent(up, true));
            } finally { down.recycle(); up.recycle(); }
            if (!clicked.await(10, TimeUnit.SECONDS)) {
                // Some headless emulators ignore UiAutomation touch injection. Use the
                // shell input service as a fallback, still requiring a real page tap.
                try (android.os.ParcelFileDescriptor ignored = automation.executeShellCommand("input tap " + p[0] + " " + p[1])) { }
            }
            assertTrue("Page did not receive the injected tap", clicked.await(45, TimeUnit.SECONDS));
            assertTrue("Popup callback not received; events=" + events, popup.await(45, TimeUnit.SECONDS));
            assertTrue("Expected gesture load before new session; events=" + events, events.size() >= 2 && events.get(0).equals("request:about:blank:true") && events.get(1).equals("session:about:blank"));
            activity.onActivity(a -> { if (child.get() != null && child.get().isOpen()) child.get().close(); if (parent.get().isOpen()) parent.get().close(); });
        }
    }
}
