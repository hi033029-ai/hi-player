package com.example.browser.gecko

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.GeckoSession

/** One consent request at a time; decisions are not persisted, including in private tabs. */
internal class GeckoPermissionUi(private val android: GeckoRuntimePermissionLauncher) : GeckoPermissionHost, AutoCloseable {
    internal sealed class Request {
        class Site(val origin: String, val label: String, val privateMode: Boolean, val permissions: List<String>, val complete: (Boolean) -> Unit) : Request()
        class Media(val origin: String, val video: List<String>, val audio: List<String>, val complete: (Int?, Int?) -> Unit) : Request()
    }
    var request by mutableStateOf<Request?>(null)
        private set
    private var closed = false

    override fun requestAndroid(permissions: List<String>, complete: (Boolean) -> Unit) {
        if (closed) complete(false) else android.request(permissions, complete)
    }
    override fun requestSite(origin: String, type: Int, privateMode: Boolean, complete: (Boolean) -> Unit) {
        val details = when (type) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "Use your location" to listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "Show notifications" to if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "Store data on this device" to emptyList()
            else -> null
        }
        if (closed || request != null || details == null) { complete(false); return }
        request = Request.Site(origin, details.first, privateMode, details.second, complete)
    }
    override fun requestMedia(origin: String, video: List<String>, audio: List<String>, complete: (Int?, Int?) -> Unit) {
        if (closed || request != null || (video.isEmpty() && audio.isEmpty())) { complete(null, null); return }
        request = Request.Media(origin, video, audio, complete)
    }
    fun deny() {
        val current = request ?: return
        request = null
        when (current) {
            is Request.Site -> current.complete(false)
            is Request.Media -> current.complete(null, null)
        }
    }
    fun allow(video: Int?, audio: Int?) {
        val current = request ?: return
        request = null
        when (current) {
            is Request.Site -> android.request(current.permissions, current.complete)
            is Request.Media -> {
                if ((current.video.isNotEmpty() && (video == null || video !in current.video.indices)) || (current.audio.isNotEmpty() && (audio == null || audio !in current.audio.indices))) {
                    current.complete(null, null); return
                }
                val permissions = buildList {
                    if (current.video.isNotEmpty()) add(Manifest.permission.CAMERA)
                    if (current.audio.isNotEmpty()) add(Manifest.permission.RECORD_AUDIO)
                }
                android.request(permissions) { granted -> current.complete(if (granted) video else null, if (granted) audio else null) }
            }
        }
    }
    override fun close() { if (closed) return; closed = true; deny(); android.close() }
}

@Composable
internal fun GeckoPermissionDialog(ui: GeckoPermissionUi) {
    val request = ui.request ?: return
    var video by remember(request) { mutableIntStateOf(0) }
    var audio by remember(request) { mutableIntStateOf(0) }
    val origin = when (request) { is GeckoPermissionUi.Request.Site -> request.origin; is GeckoPermissionUi.Request.Media -> request.origin }
    AlertDialog(
        onDismissRequest = ui::deny,
        title = { Text("Website permission") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(origin)
                when (request) {
                    is GeckoPermissionUi.Request.Site -> {
                        Text(request.label)
                        if (request.privateMode) Text("Private tab")
                    }
                    is GeckoPermissionUi.Request.Media -> {
                        if (request.video.isNotEmpty()) {
                            Text("Camera")
                            request.video.forEachIndexed { index, name ->
                                Row(Modifier.fillMaxWidth().selectable(selected = video == index, onClick = { video = index }), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = video == index, onClick = null); Text(name.ifBlank { "Camera ${index + 1}" })
                                }
                            }
                        }
                        if (request.audio.isNotEmpty()) {
                            Text("Microphone")
                            request.audio.forEachIndexed { index, name ->
                                Row(Modifier.fillMaxWidth().selectable(selected = audio == index, onClick = { audio = index }), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = audio == index, onClick = null); Text(name.ifBlank { "Microphone ${index + 1}" })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { ui.allow(video, audio) }) { Text("Allow") } },
        dismissButton = { TextButton(onClick = ui::deny) { Text("Not now") } },
    )
}
