package com.example.browser.gecko

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import java.util.UUID

/** Parse only URIs returned by the system picker. Cancellation never produces a selection. */
internal fun geckoPickerResult(result: ActivityResult): List<Uri>? {
    if (result.resultCode != Activity.RESULT_OK) return null
    val data = result.data ?: return null
    val resultUris = mutableListOf<Uri>()
    data.data?.let(resultUris::add)
    data.clipData?.let { clip -> for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(resultUris::add) }
    return resultUris.distinct().takeIf { it.isNotEmpty() }
}

/**
 * Separate ActivityResultRegistry keys prevent a late result from an old page or activity
 * from being delivered to a new request. The browser host owns this alongside GeckoFilePicker
 * and closes both on disposal. Register/unregister and callbacks must run on the main thread.
 */
internal class GeckoFilePickerLauncher(private val registry: ActivityResultRegistry) : AutoCloseable {
    private data class Pending(val launcher: ActivityResultLauncher<Intent>, val callback: (List<Uri>?) -> Unit)
    private val requests = mutableMapOf<String, Pending>()
    private var closed = false

    @MainThread fun launch(intent: Intent, callback: (List<Uri>?) -> Unit) {
        check(!closed)
        val key = "hi-gecko-picker-${UUID.randomUUID()}"
        val launcher = registry.register(key, ActivityResultContracts.StartActivityForResult()) { result ->
            val request = requests.remove(key) ?: return@register
            request.launcher.unregister()
            request.callback(geckoPickerResult(result))
        }
        requests[key] = Pending(launcher, callback)
        try { launcher.launch(intent) } catch (error: Exception) {
            requests.remove(key)
            launcher.unregister()
            throw error
        }
    }

    @MainThread override fun close() {
        if (closed) return
        closed = true
        val pending = requests.values.toList()
        requests.clear()
        pending.forEach { request ->
            runCatching { request.launcher.unregister() }
            runCatching { request.callback(null) }
        }
    }
}
