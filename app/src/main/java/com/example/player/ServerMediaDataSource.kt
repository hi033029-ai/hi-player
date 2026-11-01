package com.example.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.example.data.NetworkMediaPolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Range playback with origin/path-scoped server credentials, never automatic forwarding. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ServerMediaDataSource : BaseDataSource(true) {
    companion object {
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    }
    private var response: Response? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var original: Uri? = null
    private var expected = emptyMap<String, String>()
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false
    private fun requireSession() {
        if (original?.let { RemoteStreamHeadersRegistry.headersFor(it) } != expected || expected.isEmpty()) throw IOException("Server connection changed. Reopen the library.")
    }
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        original = dataSpec.uri
        expected = RemoteStreamHeadersRegistry.headersFor(dataSpec.uri)
        requireSession()
        val end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length - 1 else null
        val range = if (dataSpec.position > 0 || end != null) "bytes=${dataSpec.position}-${end ?: ""}" else null
        var url = dataSpec.uri.toString()
        try {
            var accepted: Response? = null
            var hops = 0
            var leftOriginalOrigin = false
            while (accepted == null) {
                requireSession()
                val builder = Request.Builder().url(url).header("User-Agent", "HiPlayer/Android").header("Accept", "*/*")
                range?.let { builder.header("Range", it) }
                // Even another configured server never receives credentials as a redirect target.
                if (!leftOriginalOrigin && NetworkMediaPolicy.sameOrigin(dataSpec.uri.toString(), url)) {
                    val scoped = RemoteStreamHeadersRegistry.headersFor(Uri.parse(url))
                    if (scoped == expected) expected.forEach { (name, value) -> builder.header(name, value) }
                }
                val candidate = client.newCall(builder.build()).execute()
                if (candidate.code in setOf(301, 302, 303, 307, 308)) {
                    val next = candidate.header("Location")?.let { candidate.request.url.resolve(it) }
                    candidate.close()
                    if (next == null || next.username.isNotEmpty() || next.password.isNotEmpty() || ++hops > 5 ||
                        (!NetworkMediaPolicy.sameOrigin(url, next.toString()) && next.scheme != "https") ||
                        (url.startsWith("https://") && next.scheme != "https")) throw IOException("Unsafe server playback redirect")
                    if (!NetworkMediaPolicy.sameOrigin(dataSpec.uri.toString(), next.toString())) leftOriginalOrigin = true
                    url = next.toString(); continue
                }
                if (!candidate.isSuccessful) { candidate.close(); throw IOException("Server media unavailable. Check access or reconnect.") }
                accepted = candidate
            }
            requireSession()
            if (accepted.code == 206 && accepted.header("Content-Range")?.startsWith("bytes ${dataSpec.position}-") != true) {
                accepted.close(); throw IOException("Invalid server seek range")
            }
            response = accepted
            input = accepted.body?.byteStream() ?: throw IOException("Empty server response")
            var skip = if (accepted.code == 200) dataSpec.position else 0L
            val buffer = ByteArray(8192)
            while (skip > 0) { requireSession(); val count = input!!.read(buffer, 0, minOf(buffer.size.toLong(), skip).toInt())
                if (count < 0) throw IOException("Seek beyond server file length"); skip -= count }
            val length = accepted.body?.contentLength() ?: -1
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else if (length >= 0)
                (length - if (accepted.code == 200) dataSpec.position else 0).coerceAtLeast(0) else C.LENGTH_UNSET.toLong()
            uri = Uri.parse(url); opened = true; transferStarted(dataSpec)
            return remaining
        } catch (error: Exception) { close(); throw error }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        requireSession()
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val result = input?.read(buffer, offset, count) ?: C.RESULT_END_OF_INPUT
        if (result > 0) { if (remaining != C.LENGTH_UNSET.toLong()) remaining -= result; bytesTransferred(result) }
        return result
    }
    override fun getUri() = uri
    override fun getResponseHeaders(): Map<String, List<String>> = response?.headers?.toMultimap().orEmpty()
    override fun close() {
        input = null; response?.close(); response = null; uri = null; original = null; expected = emptyMap()
        if (opened) { opened = false; transferEnded() }
    }
}
