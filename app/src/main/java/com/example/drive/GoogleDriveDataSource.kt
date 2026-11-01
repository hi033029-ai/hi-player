package com.example.drive

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

/** Range-based playback. Credentials never enter URLs, history or generic stream-header storage. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class GoogleDriveDataSource(private val context: Context) : BaseDataSource(true) {
    private var response: Response? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var owner: GoogleDriveSession.Owner? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        require(GoogleDrivePolicy.isMediaUrl(dataSpec.uri.toString())) { "Not a Drive media URL" }
        val account = GoogleDriveSession.owner() ?: throw IOException("Sign in to Google to play Drive files.")
        owner = account
        val end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length - 1 else null
        val range = if (dataSpec.position > 0 || end != null) "bytes=${dataSpec.position}-${end ?: ""}" else null
        var url = dataSpec.uri.toString()
        try {
            var accepted: Response? = null
            var refreshed = false
            var hop = 0
            while (accepted == null) {
                GoogleDriveSession.requireOwner(account)
                val builder = Request.Builder().url(url)
                range?.let { builder.header("Range", it) }
                // Redirected CDN requests never receive a bearer credential.
                if (hop == 0) builder.header("Authorization", "Bearer ${GoogleDriveSession.token(context, account, refreshed)}")
                val candidate = GoogleDriveApi.http.newCall(builder.build()).execute()
                if (candidate.code == 401 && hop == 0 && !refreshed) { candidate.close(); refreshed = true; continue }
                if (candidate.code in setOf(301, 302, 303, 307, 308)) {
                    val next = candidate.header("Location")?.let { candidate.request.url.resolve(it)?.toString() }
                    candidate.close()
                    if (next == null || !GoogleDrivePolicy.safeRedirect(next) || ++hop > 5) throw IOException("Unsafe or unsupported Drive redirect.")
                    url = next; continue
                }
                if (!candidate.isSuccessful) { val code = candidate.code; candidate.close(); throw IOException("Drive playback unavailable ($code). Reconnect Drive or choose another file.") }
                accepted = candidate
            }
            GoogleDriveSession.requireOwner(account)
            if (accepted.code == 206 && accepted.header("Content-Range")?.startsWith("bytes ${dataSpec.position}-") != true) {
                accepted.close(); throw IOException("Drive returned an invalid seek range")
            }
            response = accepted
            input = accepted.body?.byteStream() ?: throw IOException("Empty Drive response")
            var skip = if (accepted.code == 200) dataSpec.position else 0L
            val buffer = ByteArray(8192)
            while (skip > 0) {
                GoogleDriveSession.requireOwner(account)
                val read = input!!.read(buffer, 0, minOf(buffer.size.toLong(), skip).toInt())
                if (read < 0) throw IOException("Drive seek is beyond the file length")
                skip -= read
            }
            val length = accepted.body?.contentLength() ?: -1L
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
                else if (length >= 0) (length - if (accepted.code == 200) dataSpec.position else 0).coerceAtLeast(0)
                else C.LENGTH_UNSET.toLong()
            uri = dataSpec.uri; opened = true; transferStarted(dataSpec)
            return remaining
        } catch (error: Exception) { close(); throw error }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        owner?.let(GoogleDriveSession::requireOwner)
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val read = input?.read(buffer, offset, count) ?: C.RESULT_END_OF_INPUT
        if (read > 0) { if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read; bytesTransferred(read) }
        return read
    }
    override fun getUri(): Uri? = uri
    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
    override fun close() {
        input = null; response?.close(); response = null; owner = null; uri = null
        if (opened) { opened = false; transferEnded() }
    }
}

/** Dispatch Drive before the generic browser/server-header resolver. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AccountAwareHttpDataSource(private val context: Context, private val fallback: DataSource.Factory) : DataSource {
    private var active: DataSource? = null
    private val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()
    override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) { listeners += listener; active?.addTransferListener(listener) }
    override fun open(dataSpec: DataSpec): Long {
        val source = when {
            dataSpec.uri.scheme == "hiplayer-cloud" -> com.example.connections.CloudMediaDataSource()
            dataSpec.uri.scheme == "hiplayer-smb" -> com.example.connections.SmbMediaDataSource()
            GoogleDrivePolicy.isMediaUrl(dataSpec.uri.toString()) -> GoogleDriveDataSource(context)
            com.example.player.RemoteStreamHeadersRegistry.headersFor(dataSpec.uri).isNotEmpty() -> com.example.player.ServerMediaDataSource()
            else -> fallback.createDataSource()
        }
        active = source; listeners.forEach(source::addTransferListener); return source.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = checkNotNull(active).read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()
    override fun close() { try { active?.close() } finally { active = null } }
}
