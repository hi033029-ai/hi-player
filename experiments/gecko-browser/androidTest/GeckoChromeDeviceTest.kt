package com.example.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.OrbitBrowserFilterEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoChromeDeviceTest {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        node ?: return null
        if (node.viewIdResourceName == label || node.contentDescription?.toString() == label ||
            node.text?.toString() == label) return node
        for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
        return null
    }

    private fun control(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            find(root, label)?.let { return it }
            if (label == "Browser menu" || label == "Browser home" || label.startsWith("Open tabs")) {
                find(root, "Show browser toolbar")?.let { clickNode(it) }
            }
            SystemClock.sleep(100)
        }
        error("Browser control did not appear: $label")
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        checkNotNull(target).let {
            it.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            check(it.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
    }

    private fun click(label: String) = clickNode(control(label))

    private fun submit(url: String) {
        val id = "browser_address_input"
        click(id)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, url)
        }
        check(control(id).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        check(control(id).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id))
    }
    @Test(timeout = 150_000) fun addressTabsBookmarksAndPlayerHandoffThroughRealAppBrowser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("orbit_browser",Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.resolve("orbit-filters.txt").writeText("/adserver/\n")
        OrbitBrowserFilterEngine.refreshPlayFilters(context)
        FixtureServer(context).use { server ->
            ActivityScenario.launch(GeckoChromeTestActivity::class.java).use { scenario ->
                lateinit var activity: GeckoChromeTestActivity
                scenario.onActivity { activity = it }
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
                assertFalse(control("browser_address_input").isFocused)
                click("Browser menu")
                click("Add bookmark")
                click("Open tabs, 1 tab")
                click("New tab")
                assertTrue(control("Hi Browser").isVisibleToUser)
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
            }
        }
    }
}
