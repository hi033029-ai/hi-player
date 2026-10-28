package com.example.downloads

internal data class BrowserDownloadRequest(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String?,
    val fileName: String,
    val totalBytes: Long = 0,
    val referer: String = "",
)
