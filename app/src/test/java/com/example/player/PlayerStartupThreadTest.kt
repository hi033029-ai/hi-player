package com.example.player

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf
import android.os.Handler
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerStartupThreadTest {
    class VideoProvider : ContentProvider() {
        val openedOnMain = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            if (Looper.myLooper() == Looper.getMainLooper()) openedOnMain.set(true)
            entered.countDown()
            try { finish.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            throw FileNotFoundException("Test provider has no video")
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri) = "video/mp4"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @OptIn(UnstableApi::class)
    @Test fun preparingContentVideoDoesNotOpenProviderOnMainThread() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = VideoProvider()
        provider.attachInfo(context, ProviderInfo().apply { authority = "player.startup.test" })
        ShadowContentResolver.registerProviderInternal("player.startup.test", provider)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val engine = HiPlayerEngine(context, scope)
        try {
            // Exercise loader/provider I/O without requiring a video window in this JVM test.
            engine.setBackgroundPlay(true)
            engine.prepareMedia(Uri.parse("content://player.startup.test/video/1"), mediaMimeType = "video/mp4")
            assertTrue("Test must actually reach the slow provider", provider.entered.await(4, TimeUnit.SECONDS))
            val heartbeat = AtomicBoolean(false)
            Handler(Looper.getMainLooper()).post { heartbeat.set(true) }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("UI must process taps while provider is blocked", heartbeat.get())
            assertFalse("Opening a content provider must not block the UI thread", provider.openedOnMain.get())
        } finally {
            provider.finish.countDown()
            engine.release()
            scope.cancel()
        }
    }
}
