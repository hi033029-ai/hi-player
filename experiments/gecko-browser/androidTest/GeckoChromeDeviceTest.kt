package com.example.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.OrbitBrowserFilterEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoChromeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun roots(view: View): Sequence<SemanticsNode> = sequence {
        if (view is RootForTest) yield(view.semanticsOwner.rootSemanticsNode)
        else if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(roots(view.getChildAt(index)))
    }

    private fun nodes(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(node)
        for (child in node.children) yieldAll(nodes(child))
    }

    private fun find(label: String): SemanticsNode? =
        WindowInspector.getGlobalWindowViews().asReversed().asSequence()
            .flatMap(::roots).flatMap(::nodes).firstOrNull { node ->
                val config = node.config
                config.getOrNull(SemanticsProperties.TestTag) == label ||
                    config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                    config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            }

    private fun control(label: String, action: (SemanticsNode) -> Boolean) {
        Log.i("GeckoChromeTest", "Checking $label")
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var done = false
            instrumentation.runOnMainSync { find(label)?.let { done = action(it) } }
            if (done) return
            SystemClock.sleep(100)
        }
        error("Browser control did not reach the expected state: $label")
    }

    private fun click(label: String) = control(label) { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
    }

    private fun submit(url: String) {
        val id = "browser_address_input"
        click(id)
        control(id) { it.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(AnnotatedString(url)) == true }
        control(id) { it.config.getOrNull(SemanticsProperties.EditableText)?.text == url &&
            it.config.getOrNull(SemanticsActions.OnImeAction)?.action?.invoke() == true }
    }
    @Test(timeout = 150_000) fun addressTabsBookmarksAndPlayerHandoffThroughRealAppBrowser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("orbit_browser",Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.resolve("orbit-filters.txt").writeText("/adserver/\n")
        OrbitBrowserFilterEngine.refreshPlayFilters(context)
        FixtureServer(context).use { server ->
            val scenario = ActivityScenario.launch(GeckoChromeTestActivity::class.java)
            lateinit var activity: GeckoChromeTestActivity
            scenario.onActivity { activity = it }
            val observer = Thread {
                try {
                    Thread.sleep(30_000)
                    Log.w("GeckoChromeTest", "Main thread during browser check:\n" +
                        Looper.getMainLooper().thread.stackTrace.joinToString("\n"))
                } catch (_: InterruptedException) { }
            }
            observer.start()
            try {
                fun pageIs(test: (GeckoTabSnapshot) -> Boolean): Boolean {
                    var matches = false
                    scenario.onActivity { current -> current.owner.acquire()?.coordinator?.activeSnapshot?.let { matches = test(it) } }
                    return matches
                }
                fun awaitPage(timeoutMs: Long, test: (GeckoTabSnapshot) -> Boolean) {
                    val deadline = SystemClock.elapsedRealtime() + timeoutMs
                    while (SystemClock.elapsedRealtime() < deadline) {
                        if (pageIs(test)) return
                        SystemClock.sleep(100)
                    }
                    fail("Browser page snapshot did not reach the expected state within ${timeoutMs}ms")
                }
                val page = server.root + "/bookmark-page"
                submit(page)
                awaitPage(90_000) { it.url == page && it.title == "Bookmark fixture" && !it.loading }
                control("browser_address_input") { it.config.getOrNull(SemanticsProperties.Focused) == false }
                click("Browser menu")
                click("Add bookmark")
                click("Open tabs, 1 tab")
                click("New tab")
                control("Hi Browser") { true }
                click("Open tabs, 2 tabs")
                click("browser_tab_1")
                awaitPage(20_000) { it.url == page }
                click("Browser home")
                click("browser_bookmark_0")
                awaitPage(20_000) { it.url == page }
                submit(server.root + "/video.mp4")
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (activity.playedUrl == null && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(100)
                }
                assertEquals(server.root + "/video.mp4", activity.playedUrl)
            } finally {
                activity.runOnUiThread { activity.finish() }
                observer.interrupt()
            }
        }
    }
}
