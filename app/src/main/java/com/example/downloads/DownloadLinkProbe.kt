package com.example.downloads

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Header-only preview: never starts downloading the body before the user confirms. */
internal object DownloadLinkProbe {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    fun inspect(request: BrowserDownloadRequest, cookie: String): BrowserDownloadRequest {
        var url = Request.Builder().url(request.url).build().url
        val origin = url
        repeat(8) {
            val builder = Request.Builder().url(url).head().header("User-Agent", request.userAgent.ifBlank { "Hi Browser" })
            if (HttpDownloadTransfer.sameOrigin(origin, url)) {
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                if (request.referer.isNotBlank()) builder.header("Referer", request.referer)
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    val next = response.header("Location")?.let(url::resolve) ?: return request
                    if (url.isHttps && !next.isHttps) return request
                    url = next
                } else {
                    if (!response.isSuccessful) return request
                    val mime = response.header("Content-Type")?.takeUnless(DownloadFileResolver::isGenericMime) ?: request.mimeType
                    val result = DownloadFileResolver.resolve(request.fileName, url.toString(), mime, response.header("Content-Disposition"))
                    return request.copy(fileName = result.fileName, mimeType = result.mimeType,
                        totalBytes = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } ?: request.totalBytes)
                }
            }
        }
        return request
    }
}
