package com.example.connections

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Opaque media URIs resolve to short-lived content URLs without provider bearer headers. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CloudMediaDataSource : BaseDataSource(true) {
    private var provider: CloudProvider? = null; private var sessionId: String? = null
    private var response: Response? = null; private var uri: Uri? = null; private var started = false
    private var remaining = C.LENGTH_UNSET.toLong()
    private fun guard() { PersonalCloud.requireSession(checkNotNull(provider), checkNotNull(sessionId)) }
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val p = CloudProvider.entries.firstOrNull { it.key == dataSpec.uri.host } ?: throw IOException("Unknown cloud source")
        val parts = dataSpec.uri.pathSegments; require(parts.size == 2 && dataSpec.uri.query == null)
        provider = p; sessionId = parts[0]; uri = dataSpec.uri
        try {
            guard(); var url = CloudFiles.download(p, parts[0], parts[1]); var hops = 0
            while (true) {
                guard(); require(StoragePolicy.download(url))
                val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
                if (dataSpec.position > 0 || dataSpec.length != C.LENGTH_UNSET.toLong()) builder.header("Range", "bytes=${dataSpec.position}-${if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length - 1 else ""}")
                // Never forward a Graph/Dropbox token or a caller's generic request headers.
                val candidate = PersonalCloud.http.newCall(builder.build()).execute()
                if (candidate.code in setOf(301,302,303,307,308)) {
                    val target = candidate.header("Location")?.let { candidate.request.url.resolve(it) }; candidate.close()
                    if (++hops > 5 || target == null || !StoragePolicy.download(target.toString())) throw IOException("Unsafe cloud content redirect")
                    url = target.toString(); continue
                }
                if (!candidate.isSuccessful) { candidate.close(); throw IOException("Cloud file unavailable. Reconnect and retry.") }
                response = candidate; break
            }
            guard(); val res = checkNotNull(response)
            if (res.code == 206 && res.header("Content-Range")?.startsWith("bytes ${dataSpec.position}-") != true) throw IOException("Invalid cloud seek response")
            val input = res.body?.byteStream() ?: throw IOException("No cloud content")
            var skip = if (res.code == 200) dataSpec.position else 0L; val buffer = ByteArray(8192)
            while (skip > 0) { guard(); val n = input.read(buffer,0,minOf(skip,buffer.size.toLong()).toInt()); if (n < 0) throw IOException("Seek beyond file"); skip -= n }
            val length = res.body?.contentLength() ?: -1
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else if (length >= 0) (length - if (res.code == 200) dataSpec.position else 0).coerceAtLeast(0) else C.LENGTH_UNSET.toLong()
            started = true; transferStarted(dataSpec); return remaining
        } catch (_: Exception) { close(); throw IOException("Couldn't open cloud media. Reconnect storage and try again.") }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        try { guard(); if (remaining == 0L) return C.RESULT_END_OF_INPUT
            val n = response?.body?.byteStream()?.read(buffer,offset,if (remaining < 0) length else minOf(length.toLong(),remaining).toInt()) ?: C.RESULT_END_OF_INPUT
            guard()
            if (n > 0) { if (remaining >= 0) remaining -= n; bytesTransferred(n) }; return n
        } catch (_: Exception) { throw IOException("Cloud session or network unavailable. Reconnect and retry.") }
    }
    override fun getUri() = uri // Never expose signed content URLs to player diagnostics/history.
    override fun close() { response?.close(); response = null; uri = null; provider = null; sessionId = null; if (started) { started = false; transferEnded() } }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class SmbMediaDataSource : BaseDataSource(true) {
    private var handle: SmbHandle? = null; private var file: com.hierynomus.smbj.share.File? = null
    private var uri: Uri? = null; private var position = 0L; private var remaining = 0L; private var started = false
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec); uri = dataSpec.uri
        try {
            val id = checkNotNull(dataSpec.uri.host); require(dataSpec.uri.pathSegments.size == 1 && dataSpec.uri.query == null)
            val path = StoragePolicy.smbPath(dataSpec.uri.pathSegments.single())
            val share = SmbStorage.open(id); handle = share
            val size = share.share.getFileInformation(path).standardInformation.endOfFile
            position = dataSpec.position; require(position <= size)
            file = share.file(path); remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - position else minOf(dataSpec.length, size - position)
            SmbStorage.requireActive(id); started = true; transferStarted(dataSpec); return remaining
        } catch (_: Exception) { close(); throw IOException("Couldn't open network media. Check the share and reconnect.") }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        try {
            SmbStorage.requireActive(checkNotNull(uri?.host)); if (remaining == 0L) return C.RESULT_END_OF_INPUT
            val count = checkNotNull(file).read(buffer, position, offset, minOf(length.toLong(), remaining).toInt())
            SmbStorage.requireActive(checkNotNull(uri?.host))
            if (count <= 0) return C.RESULT_END_OF_INPUT
            position += count; remaining -= count; bytesTransferred(count); return count
        } catch (_: Exception) { throw IOException("Network folder unavailable. Reconnect and retry.") }
    }
    override fun getUri() = uri
    override fun close() { runCatching { file?.close() }; file = null; handle?.close(); handle = null; uri = null; if (started) { started = false; transferEnded() } }
}
