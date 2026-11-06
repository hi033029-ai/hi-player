package com.example.browser.gecko

import androidx.annotation.MainThread
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.net.URI
import java.util.Locale

/** Display only the origin, never a full path or credential-bearing query. */
internal fun geckoPermissionOrigin(url: String): String? = runCatching {
    val uri = URI(url)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT)
    if (scheme != "https" || host.isNullOrBlank() || uri.userInfo != null) return null
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    "https://$host$port"
}.getOrNull()

/**
 * Host callbacks must run on the UI thread and show user-facing consent. They must check Android
 * runtime permission before granting capture. A false decision includes cancellation/dismissal.
 */
internal interface GeckoPermissionHost {
    fun requestAndroid(permissions: List<String>, complete: (Boolean) -> Unit)
    fun requestSite(origin: String, type: Int, privateMode: Boolean, complete: (Boolean) -> Unit)
    fun requestMedia(origin: String, video: List<String>, audio: List<String>, complete: (Int?, Int?) -> Unit)
}

/** Gate all asynchronous replies so dismissals and duplicate callbacks cannot grant later. */
internal class GeckoPermissionGate : AutoCloseable {
    private var cancelPending: (() -> Unit)? = null
    private var closed = false
    fun start(cancel: () -> Unit): Boolean {
        if (closed || cancelPending != null) return false
        cancelPending = cancel
        return true
    }
    fun finish(cancel: () -> Unit): Boolean {
        if (cancelPending !== cancel) return false
        cancelPending = null
        return true
    }
    override fun close() {
        if (closed) return
        closed = true
        val cancel = cancelPending
        cancelPending = null
        cancel?.invoke()
    }
}

/** Default deny; only one pending request at a time, with stale or duplicate responses ignored. */
internal class GeckoPermissionBroker(private val host: GeckoPermissionHost) : GeckoSession.PermissionDelegate, AutoCloseable {
    private val gate = GeckoPermissionGate()

    @MainThread override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: GeckoSession.PermissionDelegate.Callback) {
        val list = permissions?.distinct().orEmpty()
        val supported = setOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.CAMERA", "android.permission.RECORD_AUDIO")
        if (list.isEmpty() || list.any { it !in supported }) { callback.reject(); return }
        val cancel: () -> Unit = { callback.reject() }
        if (!gate.start(cancel)) { callback.reject(); return }
        try { host.requestAndroid(list) { allowed -> if (gate.finish(cancel)) { if (allowed) callback.grant() else callback.reject() } } }
        catch (_: Exception) { if (gate.finish(cancel)) callback.reject() }
    }

    @MainThread override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int> {
        val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        val origin = geckoPermissionOrigin(perm.uri) ?: return GeckoResult.fromValue(deny)
        val result = GeckoResult<Int>()
        val cancel: () -> Unit = { result.complete(deny) }
        if (!gate.start(cancel)) return GeckoResult.fromValue(deny)
        try { host.requestSite(origin, perm.permission, perm.privateMode) { allowed ->
            if (gate.finish(cancel)) result.complete(if (allowed) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW else deny)
        } } catch (_: Exception) { if (gate.finish(cancel)) result.complete(deny) }
        return result
    }

    @MainThread override fun onMediaPermissionRequest(session: GeckoSession, uri: String, video: Array<out GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?, callback: GeckoSession.PermissionDelegate.MediaCallback) {
        val origin = geckoPermissionOrigin(uri) ?: run { callback.reject(); return }
        val videos = video.orEmpty().filter { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA }
        val audios = audio.orEmpty().filter { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
        // Reject unsupported screen/audio capture, and avoid silently accepting a partial request.
        if ((video != null && videos.isEmpty()) || (audio != null && audios.isEmpty()) || (video == null && audio == null)) { callback.reject(); return }
        val cancel: () -> Unit = { callback.reject() }
        if (!gate.start(cancel)) { callback.reject(); return }
        try { host.requestMedia(origin, videos.map { it.name.orEmpty() }, audios.map { it.name.orEmpty() }) { v, a ->
            if (gate.finish(cancel)) {
                val selectedVideo = v?.let { videos.getOrNull(it) }
                val selectedAudio = a?.let { audios.getOrNull(it) }
                if ((video != null && selectedVideo == null) || (audio != null && selectedAudio == null)) callback.reject()
                else callback.grant(selectedVideo, selectedAudio)
            }
        } } catch (_: Exception) { if (gate.finish(cancel)) callback.reject() }
    }

    @MainThread override fun close() = gate.close()
}
