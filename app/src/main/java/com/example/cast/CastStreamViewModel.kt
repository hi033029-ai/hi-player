package com.example.cast

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.model.StreamUrlClassifier
import com.example.player.RemoteStreamHeadersRegistry
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CastStreamState(
    val available: Boolean = false, val connected: Boolean = false,
    val device: String? = null, val title: String? = null,
    val playing: Boolean = false, val busy: Boolean = false,
    val message: String? = null,
)

/** Direct URL sender. Never forwards the phone's Google tokens, cookies or header registry. */
internal class CastStreamViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(CastStreamState())
    val state = _state.asStateFlow()
    private var cast: CastContext? = null
    private var client: RemoteMediaClient? = null
    private val mediaListener = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = refreshMedia()
        override fun onMetadataUpdated() = refreshMedia()
    }
    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) { _state.value = _state.value.copy(busy = true, message = "Connecting to device…") }
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = disconnected("Could not connect. Check that the TV and phone use the same Wi-Fi.")
        override fun onSessionEnding(session: CastSession) { _state.value = _state.value.copy(busy = true) }
        override fun onSessionEnded(session: CastSession, error: Int) = disconnected(null)
        override fun onSessionResuming(session: CastSession, sessionId: String) { _state.value = _state.value.copy(busy = true, message = "Reconnecting…") }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = disconnected("Reconnection failed. Select the device again.")
        override fun onSessionSuspended(session: CastSession, reason: Int) = disconnected("Connection interrupted. Select the device to reconnect.")
    }
    init {
        try {
            cast = CastContext.getSharedInstance(application)
            cast?.sessionManager?.addSessionManagerListener(sessions, CastSession::class.java)
            _state.value = CastStreamState(available = true)
            cast?.sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
        } catch (_: Exception) {
            _state.value = CastStreamState(message = "Google Cast is unavailable. Update Google Play services and try again.")
        }
    }
    private fun attach(session: CastSession) {
        client?.unregisterCallback(mediaListener)
        client = session.remoteMediaClient
        client?.registerCallback(mediaListener)
        _state.value = CastStreamState(available = true, connected = true, device = session.castDevice?.friendlyName ?: "Cast device")
        refreshMedia()
    }
    private fun disconnected(message: String?) {
        client?.unregisterCallback(mediaListener); client = null
        _state.value = CastStreamState(available = cast != null, message = message)
    }
    private fun refreshMedia() {
        _state.value = _state.value.copy(
            playing = client?.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            title = client?.mediaInfo?.metadata?.getString(MediaMetadata.KEY_TITLE),
        )
    }
    fun load(rawUrl: String, onSuccess: () -> Unit) {
        val result = StreamUrlClassifier.classify(rawUrl)
        val url = result.normalizedUrl
        if (!result.isPlayableInHiPlayer || result.requiresWebResolution || url == null) {
            _state.value = _state.value.copy(message = "Cast a direct MP4, HLS or DASH link. Webpages, local files and account-protected sources are not supported.")
            return
        }
        val rejection = CastStreamPolicy.rejection(url)
            ?: if (RemoteStreamHeadersRegistry.headersFor(Uri.parse(url)).isNotEmpty()) "This source needs private request headers. Play it on this device instead." else null
        if (rejection != null) { _state.value = _state.value.copy(message = rejection); return }
        val remote = client
        if (!_state.value.connected || remote == null) { _state.value = _state.value.copy(message = "Connect to a Cast-enabled device first."); return }
        try {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, result.displayTitle ?: Uri.parse(url).lastPathSegment ?: "Hi Player stream")
            }
            val info = MediaInfo.Builder(url).setContentType(result.mimeType ?: "video/mp4")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build()
            _state.value = _state.value.copy(busy = true, message = null)
            remote.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build()).setResultCallback { status ->
                if (client !== remote || !_state.value.connected) return@setResultCallback
                _state.value = _state.value.copy(busy = false, message = if (status.status.isSuccess) "Playing on ${_state.value.device}" else "The TV could not play this stream. Check URL reachability and receiver codec support.")
                if (status.status.isSuccess) { refreshMedia(); onSuccess() }
            }
        } catch (_: Exception) { _state.value = _state.value.copy(busy = false, message = "Could not send this stream to the TV.") }
    }
    fun togglePlayback() {
        runCatching { if (_state.value.playing) client?.pause() else client?.play() }
            .onFailure { _state.value = _state.value.copy(message = "Playback control failed. Reconnect to the device.") }
    }
    fun stop() { runCatching { client?.stop() }.onFailure { _state.value = _state.value.copy(message = "Could not stop playback.") } }
    fun disconnect() { runCatching { cast?.sessionManager?.endCurrentSession(true) }.onFailure { _state.value = _state.value.copy(message = "Could not disconnect. Use the Cast device dialog.") } }
    override fun onCleared() {
        client?.unregisterCallback(mediaListener)
        cast?.sessionManager?.removeSessionManagerListener(sessions, CastSession::class.java)
        super.onCleared()
    }
}
