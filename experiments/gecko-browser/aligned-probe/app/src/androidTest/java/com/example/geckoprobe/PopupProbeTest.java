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
        CountDownLatch ready = new CountDownLatch(1), popup = new CountDownLatch(1);
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<GeckoSession> parent = new AtomicReference<>(), child = new AtomicReference<>();
        AtomicReference<int[]> position = new AtomicReference<>();
        try (ActivityScenario<ProbeActivity> activity = ActivityScenario.launch(ProbeActivity.class)) {
            activity.onActivity(a -> {
                GeckoView view = new GeckoView(a);
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
                view.post(() -> { int[] p = new int[2]; view.getLocationOnScreen(p); position.set(new int[]{p[0] + view.getWidth()/2, p[1] + view.getHeight()/2}); });
                String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0;width:100%;height:100%}button{width:100%;height:100%;font-size:30px}</style></head><body><button onclick=\"window.open('about:blank')\">Open</button></body></html>";
                session.loadUri("data:text/html," + Uri.encode(html));
            });
            assertTrue("Local page did not finish loading", ready.await(120, TimeUnit.SECONDS));
            assertNotNull("View was not laid out", position.get());
            int[] p = position.get(); long t = SystemClock.uptimeMillis();
            InstrumentationRegistry.getInstrumentation().sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, p[0], p[1], 0));
            InstrumentationRegistry.getInstrumentation().sendPointerSync(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, p[0], p[1], 0));
            assertTrue("Popup callback not received; events=" + events, popup.await(90, TimeUnit.SECONDS));
            assertTrue("Expected gesture load before new session; events=" + events, events.size() >= 2 && events.get(0).equals("request:about:blank:true") && events.get(1).equals("session:about:blank"));
            activity.onActivity(a -> { if (child.get() != null && child.get().isOpen()) child.get().close(); if (parent.get().isOpen()) parent.get().close(); });
        }
    }
}
