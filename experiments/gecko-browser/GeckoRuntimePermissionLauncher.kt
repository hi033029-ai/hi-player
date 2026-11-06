package com.example.browser.gecko

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import java.util.UUID

/** Android permissions are requested only after a user accepts a site request. */
internal class GeckoRuntimePermissionLauncher(
    private val context: Context,
    private val registry: ActivityResultRegistry,
) : AutoCloseable {
    private data class Pending(val launcher: ActivityResultLauncher<Array<String>>, val complete: (Boolean) -> Unit)
    private val pending = mutableMapOf<String, Pending>()
    private var closed = false

    @MainThread fun request(permissions: List<String>, complete: (Boolean) -> Unit) {
        if (closed) { complete(false); return }
        val required = permissions.distinct()
        if (required.isEmpty()) { complete(true); return }
        @Suppress("DEPRECATION")
        val declared = runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions.orEmpty().toSet()
        }.getOrDefault(emptySet())
        if (required.any { it !in declared }) { complete(false); return }
        fun allGranted() = required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (allGranted()) { complete(true); return }
        val key = "hi-gecko-permission-${UUID.randomUUID()}"
        val launcher = try {
            registry.register(key, ActivityResultContracts.RequestMultiplePermissions()) { result ->
                val request = pending.remove(key) ?: return@register
                runCatching { request.launcher.unregister() }
                // Verify the operating system's state, not just a partial or stale result map.
                request.complete(required.all { result[it] == true } && allGranted())
            }
        } catch (_: Exception) { complete(false); return }
        pending[key] = Pending(launcher, complete)
        try { launcher.launch(required.toTypedArray()) } catch (_: Exception) {
            pending.remove(key)?.let { request -> runCatching { request.launcher.unregister() }; request.complete(false) }
        }
    }

    @MainThread override fun close() {
        if (closed) return
        closed = true
        val requests = pending.values.toList()
        pending.clear()
        requests.forEach { request ->
            runCatching { request.launcher.unregister() }
            runCatching { request.complete(false) }
        }
    }
}
