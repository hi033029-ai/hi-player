package com.example.downloads

import com.example.player.DownloadingFilesRegistry
import androidx.documentfile.provider.DocumentFile
import android.provider.DocumentsContract
import android.content.ContentValues
import android.content.ContentUris
import android.provider.OpenableColumns
import com.example.model.VideoItem
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.AtomicFile
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Owns the queue, durable records and bounded transfers; the foreground service owns lifetime. */
internal object BrowserDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val lock = Any()
    private val _downloads = MutableStateFlow<List<BrowserDownload>>(emptyList())
    val downloads = _downloads.asStateFlow()
    private val transfers = mutableMapOf<Long, HttpDownloadTransfer>()
    private val jobs = mutableMapOf<Long, Job>()
    private val pending = AtomicInteger(0)
    private var restored = false
    private var lastId = System.currentTimeMillis()
    private var lastPersist = 0L
    const val ACTION_ADD = "com.example.download.ADD"
    const val ACTION_PAUSE = "com.example.download.PAUSE"
    const val ACTION_RESUME = "com.example.download.RESUME"
    const val ACTION_CANCEL = "com.example.download.CANCEL"
    const val ACTION_PAUSE_ALL = "com.example.download.PAUSE_ALL"
    const val ACTION_RESUME_ALL = "com.example.download.RESUME_ALL"
    const val ACTION_REMOVE = "com.example.download.REMOVE"
    const val ACTION_RENAME = "com.example.download.RENAME"
    const val ACTION_CLEAR = "com.example.download.CLEAR"

    fun restore(context: Context) { commands.launch { synchronized(lock) { restoreLocked(context.applicationContext) } } }

    private fun folder(context: Context) = File(context.noBackupFilesDir, "browser_downloads").apply { mkdirs() }
    private fun part(context: Context, id: Long) = File(folder(context), "$id.part")
    private fun journal(context: Context) = AtomicFile(File(folder(context), "queue.json"))

    private fun restoreLocked(context: Context) {
        if (restored) return
        val records = runCatching {
            val json = journal(context).openRead().bufferedReader().use { JSONArray(it.readText()) }
            (0 until json.length()).map { index ->
                val o = json.getJSONObject(index)
                val id = o.getLong("id")
                val oldStatus = runCatching { BrowserDownloadStatus.valueOf(o.getString("status")) }.getOrDefault(BrowserDownloadStatus.PAUSED)
                val interrupted = oldStatus == BrowserDownloadStatus.RUNNING || oldStatus == BrowserDownloadStatus.QUEUED
                BrowserDownload(
                    id, o.getString("name"), o.getString("url"), o.optString("mime").ifBlank { null },
                    o.optString("ua"), o.optString("cookie"), o.optString("referer"),
                    if (oldStatus == BrowserDownloadStatus.COMPLETED) o.optLong("bytes") else part(context, id).length(),
                    o.optLong("total"), status = if (interrupted) BrowserDownloadStatus.PAUSED else oldStatus,
                    etag = o.optString("etag").ifBlank { null }, modified = o.optString("modified").ifBlank { null },
                    savedUri = o.optString("savedUri").ifBlank { null }, saveLocation = if (oldStatus != BrowserDownloadStatus.COMPLETED && o.optString("treeUri").isBlank()) "Download" else o.optString("location", "Download"),
                    destinationTreeUri = o.optString("treeUri").ifBlank { null }, customName = o.optBoolean("customName"),
                    formatChecked = o.optBoolean("formatChecked", false),
                    durationMs = o.optLong("duration"), width = o.optInt("width"), height = o.optInt("height"),
                    error = if (interrupted) "Interrupted — tap Resume" else o.optString("error").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
        _downloads.value = records
        lastId = maxOf(lastId, records.maxOfOrNull { it.id } ?: 0)
        // An interrupted MediaStore publish must not leave hidden partial destination entries.
        run {
            records.filter { it.status != BrowserDownloadStatus.COMPLETED && it.savedUri != null }.forEach {
                runCatching { context.contentResolver.delete(Uri.parse(it.savedUri), null, null) }
                replaceLocked(it.copy(savedUri = null))
            }
        }
        records.filter { it.status == BrowserDownloadStatus.COMPLETED && it.savedUri != null }.forEach {
            DownloadingFilesRegistry.completeOwned(part(context, it.id).absolutePath, it.savedUri)
        }
        restored = true
        scope.launch { repairCompletedVideos(context) }
    }

    private fun persistLocked(context: Context) {
        val json = JSONArray()
        _downloads.value.forEach { d ->
            json.put(JSONObject().apply {
                put("id", d.id); put("name", d.fileName); put("url", d.sourceUrl); put("mime", d.mimeType.orEmpty())
                put("ua", d.userAgent); put("cookie", d.cookie); put("referer", d.referer)
                put("bytes", d.downloadedBytes); put("total", d.totalBytes); put("status", d.status.name)
                put("etag", d.etag.orEmpty()); put("modified", d.modified.orEmpty()); put("savedUri", d.savedUri.orEmpty())
                put("location", d.saveLocation); put("error", d.error.orEmpty())
                put("treeUri", d.destinationTreeUri.orEmpty()); put("customName", d.customName)
                put("formatChecked", d.formatChecked); put("duration", d.durationMs); put("width", d.width); put("height", d.height)
            })
        }
        val file = journal(context)
        var output: java.io.FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (e: IOException) { output?.let(file::failWrite) }
        lastPersist = System.currentTimeMillis()
    }

    private fun replaceLocked(value: BrowserDownload) { _downloads.value = _downloads.value.map { if (it.id == value.id) value else it } }
    private fun find(id: Long) = _downloads.value.firstOrNull { it.id == id }
    private fun toast(context: Context, text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    fun enqueue(context: Context, rawUrl: String, userAgent: String, contentDisposition: String, mimeType: String? = null, referer: String = "", fileName: String? = null, totalBytes: Long = 0, destinationTreeUri: String? = null, destinationLabel: String = "Download", customName: Boolean = fileName != null) {
        val parsed = Uri.parse(rawUrl)
        if (parsed.scheme?.lowercase() !in setOf("http", "https") || parsed.host.isNullOrBlank()) {
            toast(context, "This link cannot be downloaded directly. Use the website's file download link.")
            return
        }
        val cookie = runCatching { CookieManager.getInstance().getCookie(rawUrl).orEmpty() }.getOrDefault("")
        val intent = Intent(context, BrowserDownloadService::class.java).setAction(ACTION_ADD)
            .putExtra("url", rawUrl).putExtra("ua", userAgent).putExtra("cookie", cookie).putExtra("referer", referer)
            .putExtra("name", fileName?.let(::safeFileName) ?: DownloadFileResolver.resolve(URLUtil.guessFileName(rawUrl, contentDisposition, mimeType), rawUrl, mimeType, contentDisposition).fileName).putExtra("mime", mimeType)
            .putExtra("customName", customName).putExtra("total", totalBytes).putExtra("treeUri", destinationTreeUri).putExtra("location", destinationLabel)
        start(context, intent)
    }
    internal fun safeFileName(raw: String): String = DownloadFileResolver.safeName(raw)

    private fun start(context: Context, intent: Intent) {
        runCatching { ContextCompat.startForegroundService(context.applicationContext, intent) }
            .onFailure { toast(context, "Open Hi Browser and try again to start the download") }
    }
    private fun command(context: Context, action: String, id: Long = -1) {
        start(context, Intent(context, BrowserDownloadService::class.java).setAction(action).putExtra("id", id))
    }
    fun pause(context: Context, id: Long) = command(context, ACTION_PAUSE, id)
    fun retry(context: Context, id: Long) = command(context, ACTION_RESUME, id)
    fun cancel(context: Context, id: Long) = command(context, ACTION_CANCEL, id)
    fun pauseAll(context: Context) = command(context, ACTION_PAUSE_ALL)
    fun resumeAll(context: Context) = command(context, ACTION_RESUME_ALL)
    fun removeFromList(context: Context, id: Long) = command(context, ACTION_REMOVE, id)
    fun rename(context: Context, id: Long, name: String) {
        start(context, Intent(context, BrowserDownloadService::class.java).setAction(ACTION_RENAME).putExtra("id", id).putExtra("name", safeFileName(name)))
    }
    fun copyLink(context: Context, id: Long) { find(id)?.let {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Download link", it.sourceUrl))
        toast(context, "Download link copied")
    } }
    fun clearCompleted(context: Context) = command(context, ACTION_CLEAR)
    fun hasPendingCommands() = pending.get() > 0

    fun execute(context: Context, intent: Intent?, done: () -> Unit) {
        pending.incrementAndGet()
        commands.launch {
            try {
                synchronized(lock) {
                    restoreLocked(context)
                    val id = intent?.getLongExtra("id", -1) ?: -1
                    when (intent?.action) {
                        ACTION_ADD -> {
                            val url = intent.getStringExtra("url").orEmpty()
                            if (_downloads.value.any { it.sourceUrl == url && it.isActive }) toast(context, "This download is already in your queue")
                            else if (url.isNotBlank()) {
                                val entry = BrowserDownload(++lastId, intent.getStringExtra("name") ?: "download", url,
                                    intent.getStringExtra("mime"), intent.getStringExtra("ua").orEmpty(), intent.getStringExtra("cookie").orEmpty(), intent.getStringExtra("referer").orEmpty(), totalBytes = intent.getLongExtra("total", 0),
                                    destinationTreeUri = intent.getStringExtra("treeUri"), saveLocation = intent.getStringExtra("location") ?: "Download", customName = intent.getBooleanExtra("customName", false))
                                _downloads.value = listOf(entry) + _downloads.value
                                toast(context, "Added to Hi Browser downloads")
                            }
                        }
                        ACTION_RENAME -> find(id)?.takeUnless { it.finalizing }?.let { entry ->
                            val newName = intent.getStringExtra("name")?.takeIf { it.isNotBlank() } ?: entry.fileName
                            if (entry.status == BrowserDownloadStatus.COMPLETED && entry.savedUri != null) {
                                runCatching {
                                    var uri = Uri.parse(entry.savedUri)
                                    if (entry.destinationTreeUri != null) uri = DocumentsContract.renameDocument(context.contentResolver, uri, newName) ?: error("Rename failed")
                                    else {
                                        val updated = context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }, null, null)
                                        check(updated > 0)
                                    }
                                    replaceLocked(entry.copy(fileName = newName, savedUri = uri.toString(), customName = true))
                                    scanPublishedFile(context, uri, entry.mimeType)
                                }.onFailure { toast(context, "This file could not be renamed") }
                            } else replaceLocked(entry.copy(fileName = newName, customName = true))
                        }
                        ACTION_PAUSE -> pauseLocked(id)
                        ACTION_PAUSE_ALL -> _downloads.value.filter { it.needsService }.forEach { pauseLocked(it.id) }
                        ACTION_RESUME, ACTION_RESUME_ALL -> {
                            val candidates = if (intent.action == ACTION_RESUME_ALL) _downloads.value.filter { it.status == BrowserDownloadStatus.PAUSED || it.status == BrowserDownloadStatus.FAILED } else listOfNotNull(find(id))
                            candidates.filter { !it.needsService && it.status != BrowserDownloadStatus.COMPLETED }.forEach {
                                replaceLocked(it.copy(status = BrowserDownloadStatus.QUEUED, error = null, retryCount = 0))
                            }
                        }
                        ACTION_CANCEL -> find(id)?.takeIf { it.status != BrowserDownloadStatus.COMPLETED }?.let {
                            transfers[id]?.cancel()
                            DownloadingFilesRegistry.completeOwned(part(context, id).absolutePath)
                            replaceLocked(it.copy(status = BrowserDownloadStatus.CANCELLED, speedBytesPerSecond = 0, error = null))
                            if (!jobs.containsKey(id)) part(context, id).delete()
                        }
                        ACTION_REMOVE -> find(id)?.takeUnless { it.isActive || jobs.containsKey(id) }?.let {
                            _downloads.value = _downloads.value.filterNot { d -> d.id == id }
                            if (!jobs.containsKey(id)) part(context, id).delete()
                        }
                        ACTION_CLEAR -> {
                            _downloads.value.filter { !it.isActive && !jobs.containsKey(it.id) }.forEach { part(context, it.id).delete() }
                            _downloads.value = _downloads.value.filter { it.isActive || jobs.containsKey(it.id) }
                        }
                    }
                    persistLocked(context)
                    scheduleLocked(context)
                }
            } finally { pending.decrementAndGet(); done() }
        }
    }

    private fun pauseLocked(id: Long) {
        find(id)?.takeIf { it.needsService }?.let {
            replaceLocked(it.copy(status = BrowserDownloadStatus.PAUSED, speedBytesPerSecond = 0, error = null))
            transfers[id]?.cancel()
        }
    }

    private fun scheduleLocked(context: Context) {
        _downloads.value.filter { it.status == BrowserDownloadStatus.QUEUED && !jobs.containsKey(it.id) }
            .take((2 - jobs.size).coerceAtLeast(0)).forEach { entry ->
                val transfer = HttpDownloadTransfer()
                transfers[entry.id] = transfer
                replaceLocked(entry.copy(status = BrowserDownloadStatus.RUNNING))
                val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { runTransfer(context, entry.id, transfer) }
                jobs[entry.id] = job
                job.start()
            }
    }

    private suspend fun runTransfer(context: Context, id: Long, transfer: HttpDownloadTransfer) {
        var lastBytes = part(context, id).length()
        var lastSample = System.nanoTime()
        try {
            val cached = synchronized(lock) { find(id) }
            var completed = cached?.formatChecked == true && cached.totalBytes > 0 && part(context, id).length() == cached.totalBytes
            for (attempt in 0..3) {
                if (completed) break
                transfer.checkActive()
                val entry = synchronized(lock) { find(id) } ?: return
                try {
                    val headers = buildMap {
                        if (entry.userAgent.isNotBlank()) put("User-Agent", entry.userAgent)
                        if (entry.cookie.isNotBlank()) put("Cookie", entry.cookie)
                        if (entry.referer.isNotBlank()) put("Referer", entry.referer)
                    }
                    transfer.download(entry.sourceUrl, part(context, id), headers, entry.etag, entry.modified,
                        onMetadata = { meta -> synchronized(lock) {
                            find(id)?.takeIf { it.status == BrowserDownloadStatus.RUNNING }?.let {
                                val mime = meta.mime?.takeUnless(DownloadFileResolver::isGenericMime) ?: it.mimeType
                                val resolved = DownloadFileResolver.resolve(if (it.customName) it.fileName else meta.fileName ?: it.fileName, if (it.customName) "" else meta.finalUrl ?: it.sourceUrl, mime)
                                replaceLocked(it.copy(fileName = resolved.fileName, totalBytes = meta.total, etag = meta.etag, modified = meta.modified, mimeType = resolved.mimeType, error = null))
                                persistLocked(context)
                            }
                        } },
                        onProgress = { bytes, total -> synchronized(lock) {
                            find(id)?.takeIf { it.status == BrowserDownloadStatus.RUNNING }?.let {
                                val now = System.nanoTime()
                                val speed = ((bytes - lastBytes).coerceAtLeast(0) * 1_000_000_000.0 / (now - lastSample).coerceAtLeast(1)).toLong()
                                lastBytes = bytes; lastSample = now
                                val resolved = if (bytes > 1024 && DownloadFileResolver.isGenericMime(it.mimeType)) runCatching {
                                    val prefix = ByteArray(minOf(bytes, 65536).toInt())
                                    part(context, id).inputStream().use { input -> input.read(prefix) }
                                    DownloadFileResolver.resolve(it.fileName, if (it.customName) "" else it.sourceUrl, it.mimeType, prefix = prefix)
                                }.getOrNull() else null
                                val updated = it.copy(downloadedBytes = bytes, totalBytes = if (total > 0) total else it.totalBytes, speedBytesPerSecond = speed, error = null,
                                    fileName = resolved?.fileName ?: it.fileName, mimeType = resolved?.mimeType ?: it.mimeType)
                                if (updated.isVideo) DownloadingFilesRegistry.markOwnedDownloading(part(context, id).absolutePath, bytes)
                                replaceLocked(updated)
                                if (System.currentTimeMillis() - lastPersist > 1500) persistLocked(context)
                            }
                        } })
                    completed = true
                    break
                } catch (e: IOException) {
                    transfer.checkActive()
                    if (attempt == 3 || (e is DownloadHttpException && !e.retryable)) throw e
                    synchronized(lock) { find(id)?.let { replaceLocked(it.copy(speedBytesPerSecond = 0, retryCount = attempt + 1, error = "Connection interrupted — retrying ${attempt + 1}/3")) } }
                    // Short cancellable intervals make pause/cancel immediate during backoff.
                    repeat((1 shl attempt) * 10) { delay(100); transfer.checkActive() }
                    lastBytes = part(context, id).length(); lastSample = System.nanoTime()
                }
            }
            if (completed) {
                val unfinished = synchronized(lock) { find(id) } ?: return
                val info = DownloadMediaInspector.inspect(part(context, id), unfinished)
                val current = synchronized(lock) { find(id) } ?: return
                val finalName = if (current.customName && current.fileName != unfinished.fileName) current.fileName else info.resolved.fileName
                val entry = current.copy(fileName = finalName, mimeType = info.resolved.mimeType, finalizing = true, formatChecked = true, durationMs = info.durationMs, width = info.width, height = info.height)
                transfer.checkActive()
                synchronized(lock) { if (find(id)?.status == BrowserDownloadStatus.RUNNING) replaceLocked(entry) }
                val saved = publish(context, entry, transfer)
                synchronized(lock) {
                    if (find(id)?.status == BrowserDownloadStatus.RUNNING) {
                        replaceLocked(entry.copy(status = BrowserDownloadStatus.COMPLETED, finalizing = false, downloadedBytes = part(context, id).length(), totalBytes = part(context, id).length(), speedBytesPerSecond = 0,
                            savedUri = saved.first.toString(), saveLocation = saved.second, cookie = "", userAgent = "", referer = "", error = null))
                        DownloadingFilesRegistry.completeOwned(part(context, id).absolutePath, saved.first.toString())
                        persistLocked(context)
                        part(context, id).delete()
                    } else deletePublished(context, saved.first)
                }
            }
        } catch (e: Exception) {
            synchronized(lock) {
                find(id)?.takeIf { it.status == BrowserDownloadStatus.RUNNING }?.let {
                    val message = when {
                        e is DownloadHttpException -> e.message ?: "Download failed"
                        e is java.net.UnknownHostException -> "No connection. Check your internet and tap Retry."
                        e is java.net.SocketTimeoutException -> "Connection timed out. Tap Retry to resume."
                        e.message?.contains("ENOSPC", true) == true -> "Not enough free storage. Free space and tap Retry."
                        else -> "Transfer interrupted. Check your connection or storage and tap Retry."
                    }
                    replaceLocked(it.copy(status = BrowserDownloadStatus.FAILED, downloadedBytes = part(context, id).length(), speedBytesPerSecond = 0, error = message))
                }
            }
        } finally {
            synchronized(lock) {
                jobs.remove(id); transfers.remove(id)
                val state = find(id)
                if (state == null || state.status == BrowserDownloadStatus.CANCELLED) part(context, id).delete()
                else if (state.status != BrowserDownloadStatus.COMPLETED) replaceLocked(state.copy(downloadedBytes = part(context, id).length(), speedBytesPerSecond = 0, finalizing = false))
                persistLocked(context)
                scheduleLocked(context)
            }
        }
    }

    private fun publish(context: Context, entry: BrowserDownload, transfer: HttpDownloadTransfer): Pair<Uri, String> {
        transfer.checkActive()
        if (entry.destinationTreeUri != null) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(entry.destinationTreeUri)) ?: throw IOException("Destination folder is unavailable")
            val document = folder.createFile(entry.mimeType ?: "application/octet-stream", entry.fileName) ?: throw IOException("Cannot write to destination folder")
            try {
                synchronized(lock) { find(entry.id)?.let { replaceLocked(it.copy(savedUri = document.uri.toString())); persistLocked(context) } }
                context.contentResolver.openOutputStream(document.uri, "w")?.use { output -> part(context, entry.id).inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) { transfer.checkActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                } } ?: throw IOException("Cannot write downloaded file")
                transfer.checkActive()
                return document.uri to entry.saveLocation
            } catch (e: Exception) {
                document.delete()
                synchronized(lock) { find(entry.id)?.let { replaceLocked(it.copy(savedUri = null)) } }
                throw e
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val location = Environment.DIRECTORY_DOWNLOADS
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, entry.fileName)
                put(MediaStore.Downloads.MIME_TYPE, entry.mimeType ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, location)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Could not create downloaded file")
            try {
                synchronized(lock) { find(entry.id)?.let { replaceLocked(it.copy(savedUri = uri.toString())); persistLocked(context) } }
                resolver.openOutputStream(uri, "w")?.use { output ->
                    part(context, entry.id).inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { transfer.checkActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                    }
                } ?: throw IOException("Could not write downloaded file")
                transfer.checkActive()
                if (resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) != 1) throw IOException("Could not finish downloaded file")
                scanPublishedFile(context, uri, entry.mimeType)
                return uri to location
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                synchronized(lock) { find(entry.id)?.let { replaceLocked(it.copy(savedUri = null)) } }
                throw e
            }
        }
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val dir = if (hasPermission) publicDir else throw IOException("Choose a folder or grant storage access before downloading")
        dir.mkdirs()
        var destination = File(dir, entry.fileName)
        var count = 1
        while (!destination.createNewFile()) { destination = File(dir, "${entry.fileName.substringBeforeLast('.', entry.fileName)} (${count++})" + entry.fileName.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }) }
        try {
            destination.outputStream().use { output -> part(context, entry.id).inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { transfer.checkActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
            } }
            transfer.checkActive()
            if (hasPermission) MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf(entry.mimeType), null)
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", destination) to "Download"
        } catch (e: Exception) { destination.delete(); throw e }
    }

    private fun scanPublishedFile(context: Context, uri: Uri, mime: String?) {
        val path = runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        if (!path.isNullOrBlank()) MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime), null)
    }

    private fun repairCompletedVideos(context: Context) {
        val entries = synchronized(lock) { _downloads.value.filter { it.status == BrowserDownloadStatus.COMPLETED && !it.formatChecked && it.savedUri != null } }
        entries.forEach { entry ->
            runCatching {
                val uri = Uri.parse(entry.savedUri)
                val info = DownloadMediaInspector.inspect(context, uri, entry)
                var corrected = entry
                if (info.resolved.isVideo && uri.authority == MediaStore.AUTHORITY) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, info.resolved.fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, info.resolved.mimeType)
                    }
                    if (context.contentResolver.update(uri, values, null, null) > 0) {
                        corrected = entry.copy(fileName = info.resolved.fileName, mimeType = info.resolved.mimeType,
                            durationMs = info.durationMs, width = info.width, height = info.height)
                        scanPublishedFile(context, uri, info.resolved.mimeType)
                    }
                } else if (info.resolved.isVideo && uri.authority == "${context.packageName}.provider") {
                    // Pre-Android-10 app-specific/public files are also repairable without redownloading.
                    val roots = listOfNotNull(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Hi Browser") },
                        File(context.filesDir, "Hi Browser"),
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Hi Browser"),
                    )
                    val source = roots.map { File(it, entry.fileName) }.firstOrNull { it.isFile && it.canWrite() }
                    if (source != null) {
                        var target = File(source.parentFile, info.resolved.fileName)
                        if (target != source && target.exists()) target = File(source.parentFile, "${target.nameWithoutExtension}-${entry.id}.${target.extension}")
                        if (target == source || source.renameTo(target)) {
                            val savedUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", target)
                            corrected = entry.copy(fileName = target.name, mimeType = info.resolved.mimeType, savedUri = savedUri.toString(),
                                durationMs = info.durationMs, width = info.width, height = info.height)
                            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(info.resolved.mimeType), null)
                        }
                    }
                }
                synchronized(lock) {
                    find(entry.id)?.takeIf { it.status == BrowserDownloadStatus.COMPLETED && it.savedUri == entry.savedUri }?.let {
                        replaceLocked(corrected.copy(formatChecked = true)); persistLocked(context)
                    }
                }
            }
        }
    }

    /** Both partial and completed app-owned videos remain discoverable in every app mode. */
    suspend fun libraryVideoItems(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val entries = synchronized(lock) {
            restoreLocked(context)
            _downloads.value.filter { it.isVideo && it.status != BrowserDownloadStatus.CANCELLED }
        }
        entries.mapNotNull { entry -> runCatching {
            if (entry.status != BrowserDownloadStatus.COMPLETED) {
                val file = part(context, entry.id)
                if (!file.isFile || file.length() <= 0) return@runCatching null
                DownloadingFilesRegistry.markOwnedDownloading(file.absolutePath, file.length())
                return@runCatching partialItem(context, entry)
            }
            val uri = entry.savedUri?.let(Uri::parse) ?: return@runCatching null
            val exists = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() } == true
            if (!exists) return@runCatching null
            val path = runCatching {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull().orEmpty()
            VideoItem(
                id = if (uri.authority == MediaStore.AUTHORITY) runCatching { ContentUris.parseId(uri) }.getOrDefault(entry.id) else entry.id,
                uri = uri, title = entry.fileName, mimeType = entry.mimeType ?: "video/*", sizeBytes = entry.downloadedBytes,
                durationMs = entry.durationMs, width = entry.width, height = entry.height,
                dateAdded = entry.id / 1000, folderName = entry.saveLocation.substringAfterLast('/'),
                path = path.ifBlank { if (entry.destinationTreeUri != null) uri.toString() else File(Environment.getExternalStorageDirectory(), "${entry.saveLocation}/${entry.fileName}").absolutePath },
                isNewVideo = System.currentTimeMillis() - entry.id < 86_400_000L,
            )
        }.getOrNull() }
    }

    private fun partialItem(context: Context, entry: BrowserDownload): VideoItem {
        val file = part(context, entry.id)
        return VideoItem(id = entry.id, uri = Uri.fromFile(file), title = entry.fileName, mimeType = entry.mimeType ?: "video/*",
            sizeBytes = file.length(), path = file.absolutePath, folderName = "Download", dateAdded = entry.id / 1000,
            isPartialDownload = true, downloadedBytes = file.length(), downloadTotalBytes = entry.totalBytes,
            downloadSpeedBytesPerSecond = entry.speedBytesPerSecond, width = 0, height = 0, isNewVideo = true, codec = entry.status.name.lowercase())
    }
    fun playbackItem(context: Context, uri: Uri): VideoItem? = downloads.value.firstOrNull {
        uri.toString() == it.savedUri || (uri.scheme == "file" && uri.path == part(context, it.id).absolutePath)
    }?.let { entry -> if (entry.status == BrowserDownloadStatus.COMPLETED) VideoItem(uri = uri, title = entry.fileName, mimeType = entry.mimeType ?: "video/*", path = uri.toString(), folderName = entry.saveLocation)
        else partialItem(context, entry) }

    private fun deletePublished(context: Context, uri: Uri) { runCatching { context.contentResolver.delete(uri, null, null) } }

    fun playInPlayer(context: Context, id: Long, onPlay: (String) -> Unit) {
        val entry = find(id)
        val uri = entry?.savedUri?.takeIf { entry.status == BrowserDownloadStatus.COMPLETED }?.let(Uri::parse)
            ?: entry?.let { part(context, id).takeIf { f -> f.isFile && f.length() > 0 }?.let { f ->
                DownloadingFilesRegistry.markOwnedDownloading(f.absolutePath, f.length()); Uri.fromFile(f)
            } }
        if (uri == null) toast(context, "The downloaded file is not available") else onPlay(uri.toString())
    }
    fun open(context: Context, id: Long) {
        val entry = find(id) ?: return
        val uri = entry.savedUri?.let(Uri::parse) ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, entry.mimeType ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast(context, "No installed app can open this file") }
    }
    fun share(context: Context, id: Long) {
        val entry = find(id) ?: return
        val uri = entry.savedUri?.let(Uri::parse) ?: return
        runCatching {
            val send = Intent(Intent.ACTION_SEND).setType(entry.mimeType ?: "application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = android.content.ClipData.newRawUri(entry.fileName, uri)
            context.startActivity(Intent.createChooser(send, "Share downloaded file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast(context, "Could not share this file") }
    }
    fun pauseForServiceStop(context: Context) { commands.launch { synchronized(lock) {
        _downloads.value.filter { it.needsService }.forEach { pauseLocked(it.id) }
        persistLocked(context)
    } } }
}
