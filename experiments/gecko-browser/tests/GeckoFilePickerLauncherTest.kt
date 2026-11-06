package com.example.browser.gecko

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeckoFilePickerLauncherTest {
    private class FakeRegistry : ActivityResultRegistry() {
        val codes = mutableListOf<Int>()
        override fun <I : Any?, O : Any?> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { codes += code }
    }
    @Test fun resultHandlesMultipleFilesAndCancellation() {
        val first = Uri.parse("content://test/a")
        val second = Uri.parse("content://test/b")
        val intent = Intent().setData(first).apply { clipData = ClipData.newRawUri("one", first).apply { addItem(ClipData.Item(second)) } }
        assertEquals(listOf(first, second), geckoPickerResult(ActivityResultContracts.StartActivityForResult().parseResult(Activity.RESULT_OK, intent)))
        assertNull(geckoPickerResult(ActivityResultContracts.StartActivityForResult().parseResult(Activity.RESULT_CANCELED, intent)))
    }
    @Test fun distinctKeysPreventOldResultsReachingNewRequest() {
        val registry = FakeRegistry()
        val old = GeckoFilePickerLauncher(registry)
        var oldCalls = 0
        old.launch(Intent()) { oldCalls++; assertNull(it) }
        val oldCode = registry.codes.last()
        old.close()
        var selected: List<Uri>? = null
        val next = GeckoFilePickerLauncher(registry)
        next.launch(Intent()) { selected = it }
        val nextCode = registry.codes.last()
        registry.dispatchResult(oldCode, Activity.RESULT_OK, Intent().setData(Uri.parse("content://test/old")))
        assertNull(selected)
        val uri = Uri.parse("content://test/new")
        registry.dispatchResult(nextCode, Activity.RESULT_OK, Intent().setData(uri))
        assertEquals(listOf(uri), selected)
        assertEquals(1, oldCalls)
        next.close()
    }
}
