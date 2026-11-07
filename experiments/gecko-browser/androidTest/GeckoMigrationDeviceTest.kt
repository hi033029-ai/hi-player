package com.example.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.OrbitBrowserFilterEngine
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

private class FixtureServer(private val context: Context) : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool()
    val blockedHits = AtomicInteger()
    val downloadHits = AtomicInteger()
    val root = "http://127.0.0.1:${socket.localPort}"
    init {
        pool.execute {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                client.soTimeout = 5000
                pool.execute {
                    client.use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val path = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                        val headers = mutableMapOf<String,String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            val colon = line.indexOf(':')
                            if (colon > 0) headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
                        }
                        var extra = ""
                        var mime = "text/html; charset=utf-8"
                        val content = when {
                            path.startsWith("/adserver/") -> { blockedHits.incrementAndGet(); mime = "application/javascript"; "window.blockedAdRan=true".toByteArray() }
                            path == "/allowed.js" -> { mime = "application/javascript"; "window.allowedFixture=true".toByteArray() }
                            path == "/video.mp4" -> { mime = "video/mp4"; context.assets.open("gecko_test.mp4").use { it.readBytes() } }
                            path == "/set-cookie" -> { extra = "Set-Cookie: hi_gecko_fixture=normal; Path=/; HttpOnly\r\n"; "<title>Cookie set</title>".toByteArray() }
                            path == "/check-cookie" -> "<title>cookie:${if (headers["cookie"].orEmpty().contains("hi_gecko_fixture=normal")) "present" else "missing"}</title>".toByteArray()
                            path == "/download" -> { downloadHits.incrementAndGet(); mime = "application/octet-stream"; extra = "Content-Disposition: attachment; filename=gecko-device-test.bin\r\n"; "gecko-stream-body".toByteArray() }
                            path == "/google-route" -> { extra = "Location: https://accounts.google.com/ServiceLogin\r\n"; ByteArray(0) }
                            else -> "<html><head><title>Integrated Gecko ready</title></head><body><article>Readable fixture text for Hi Player.</article><script src='/allowed.js'></script><script src='/adserver/blocked.js'></script><video controls preload='metadata' src='/video.mp4'></video></body></html>".toByteArray()
                        }
                        val status = if (path == "/google-route") "302 Found" else "200 OK"
                        val output = connection.getOutputStream()
                        output.write("HTTP/1.1 $status\r\nContent-Type: $mime\r\n${extra}Content-Length: ${content.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(content); output.flush()
                    }
                }
            }
        }
    }
    override fun close() { socket.close(); pool.shutdownNow() }
}

@RunWith(AndroidJUnit4::class)
class GeckoMigrationDeviceTest {
    private fun prepare(context: Context) {
        context.filesDir.resolve("orbit-filters.txt").writeText("/adserver/\n")
        OrbitBrowserFilterEngine.refreshPlayFilters(context)
    }
    private fun await(scenario: ActivityScenario<GeckoMigrationTestActivity>, description: String, test: (GeckoMigrationTestActivity) -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 90_000
        while (SystemClock.elapsedRealtime() < end) {
            var success = false
            scenario.onActivity { success = test(it) }
            if (success) return
            SystemClock.sleep(150)
        }
        fail(description)
    }
    @Test fun integratedFilteringMediaAndReaderUseRealGecko() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prepare(context)
        FixtureServer(context).use { server ->
            ActivityScenario.launch(GeckoMigrationTestActivity::class.java).use { activity ->
                activity.onActivity { it.services.coordinator.load(server.root + "/page") }
                await(activity, "App Gecko page did not load") { it.services.coordinator.activeSnapshot.title == "Integrated Gecko ready" && !it.services.coordinator.activeSnapshot.loading }
                await(activity, "Extension did not report HTML5 media") { it.services.coordinator.activeSnapshot.mediaUrl == server.root + "/video.mp4" }
                assertEquals("Blocked script reached the server", 0, server.blockedHits.get())
                var readerSent = false
                activity.onActivity { val tab = it.services.coordinator.controller.activeSession() as GeckoManagedTab; readerSent = GeckoFeatureBridge.requestReader(tab.engine.session) }
                assertTrue("Reader command port disconnected", readerSent)
                await(activity, "Reader result did not return through native bridge") { it.services.coordinator.activeSnapshot.readerText.orEmpty().contains("Readable fixture text") }
            }
        }
    }
    @Test fun privateCookiesStaySeparateAndDownloadIsNotRefetched() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prepare(context)
        FixtureServer(context).use { server ->
            ActivityScenario.launch(GeckoMigrationTestActivity::class.java).use { activity ->
                activity.onActivity { it.services.coordinator.load(server.root + "/set-cookie") }
                await(activity, "Cookie setup failed") { it.services.coordinator.activeSnapshot.title == "Cookie set" }
                activity.onActivity { it.services.coordinator.open(true); it.services.coordinator.load(server.root + "/check-cookie") }
                await(activity, "Private tab received a regular cookie") { it.services.coordinator.activeSnapshot.title == "cookie:missing" }
                activity.onActivity { it.services.coordinator.closeTab(it.services.coordinator.activeId); it.services.coordinator.load(server.root + "/check-cookie") }
                await(activity, "Regular session lost its cookie") { it.services.coordinator.activeSnapshot.title == "cookie:present" }
                activity.onActivity { it.services.coordinator.load(server.root + "/download") }
                await(activity, "No native file-response offer") { it.services.downloads.state is GeckoDownloadState.Offer }
                activity.onActivity { it.services.downloads.approve() }
                await(activity, "Approved stream did not finish") { (it.services.downloads.state as? GeckoDownloadState.Finished)?.outcome is GeckoDownloadOutcome.Complete }
                assertEquals("Source was re-fetched", 1, server.downloadHits.get())
            }
        }
    }
    @Test fun googleAuthRequiresExplicitSecureBrowserConsent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prepare(context)
        FixtureServer(context).use { server ->
            ActivityScenario.launch(GeckoMigrationTestActivity::class.java).use { activity ->
                activity.onActivity { it.services.coordinator.load(server.root + "/google-route") }
                await(activity, "Authentication was not routed to explicit consent") { it.services.secureRouting.pending != null }
                activity.onActivity { assertFalse(it.services.secureRouting.pending!!.external) }
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class GeckoFallbackDeviceTest {
    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }
    @Test fun android7RunsTheActualWebViewFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FixtureServer(context).use { server ->
            val intent = Intent(context, GeckoFallbackTestActivity::class.java).putExtra("url", server.root + "/page")
            ActivityScenario.launch<GeckoFallbackTestActivity>(intent).use { activity ->
                val end = SystemClock.elapsedRealtime() + 90_000
                while (SystemClock.elapsedRealtime() < end) {
                    var loaded = false
                    activity.onActivity { loaded = findWebView(it.window.decorView)?.url?.startsWith(server.root) == true }
                    if (loaded) return
                    SystemClock.sleep(150)
                }
                fail("API 24 did not create and navigate the original WebView")
            }
        }
    }
}
