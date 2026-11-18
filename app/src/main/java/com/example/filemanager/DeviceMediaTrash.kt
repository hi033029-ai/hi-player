package com.example.filemanager

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.model.FileItem
import com.example.model.FileType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject

/** Android owns the confirmation, expiry, and restoration of scoped-storage media. */
internal class DeviceMediaTrash(private val context: Context) {
    data class Entry(val uri: Uri, val name: String, val path: String, val bytes: Long, val added: Long)
    enum class Action { MOVE, RESTORE, ERASE }

    private val resolver = context.contentResolver
    private val tracked = context.getSharedPreferences("device_media_trash", Context.MODE_PRIVATE)
    private val prompts = Channel<PendingIntent>(Channel.BUFFERED)
    val requests = prompts.receiveAsFlow()
    @Volatile private var reply: CompletableDeferred<Boolean>? = null

    val awaitingConsent: Boolean get() = reply != null

    fun finishPrompt(approved: Boolean) { reply?.complete(approved) }

    @RequiresApi(30)
    fun resolve(item: FileItem): Entry? {
        if (item.isDirectory || item.fileType !in setOf(FileType.VIDEO, FileType.AUDIO, FileType.IMAGE)) return null
        val supplied = item.mediaUri
        val media = when {
            supplied?.authority == MediaStore.AUTHORITY -> supplied
            supplied?.scheme == "content" -> runCatching { MediaStore.getMediaUri(context, supplied) }.getOrNull()
            else -> null
        }
        // Register only a specific provider row. A collection or a missing path
        // must never become a bulk operation.
        val uri = media?.takeIf { it.lastPathSegment?.toLongOrNull() != null }
            ?: item.path.takeIf { it.startsWith('/') }?.let { path ->
                resolver.query(MediaStore.Files.getContentUri("external"),
                    arrayOf(BaseColumns._ID, MediaStore.MediaColumns.VOLUME_NAME),
                    "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path), null)?.use { cursor ->
                    if (!cursor.moveToFirst()) null else ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri(cursor.getString(1)), cursor.getLong(0))
                }
            } ?: return null
        return Entry(uri, item.name, item.path, item.sizeBytes, System.currentTimeMillis())
    }

    @RequiresApi(30)
    suspend fun change(entries: List<Entry>, action: Action, completed: (List<Entry>) -> Unit) {
        for (batch in entries.distinctBy { it.uri }.chunked(2000)) {
            val uris = batch.map { it.uri }
            val intent = when (action) {
                Action.MOVE -> MediaStore.createTrashRequest(resolver, uris, true)
                Action.RESTORE -> MediaStore.createTrashRequest(resolver, uris, false)
                Action.ERASE -> MediaStore.createDeleteRequest(resolver, uris)
            }
            // Record candidates before sending Android's prompt. If the activity
            // or process dies, the bin is reconstructed from actual provider state.
            if (action == Action.MOVE) remember(batch)
            val decision = CompletableDeferred<Boolean>()
            reply = decision
            val approved = try {
                prompts.send(intent)
                decision.await()
            } finally {
                reply = null
            }
            if (!approved) {
                if (action == Action.MOVE) forget(batch)
                break
            }
            if (action != Action.MOVE) forget(batch)
            completed(batch)
        }
    }

    fun visibleEntries(): List<Entry> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val entries = tracked.all.mapNotNull { (uri, value) ->
            runCatching {
                val data = JSONObject(value as String)
                Entry(Uri.parse(uri), data.getString("name"), data.getString("path"),
                    data.getLong("bytes"), data.getLong("added"))
            }.getOrNull()
        }
        // Query only media previously selected in this app, including after a restart.
        val visible = mutableListOf<Entry>()
        entries.groupBy { ContentUris.removeId(it.uri) }.forEach { (collection, group) ->
            group.chunked(300).forEach { batch ->
                val ids = batch.associateBy { ContentUris.parseId(it.uri) }
                val args = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${BaseColumns._ID} IN (${ids.keys.joinToString(",") { "?" }})")
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        ids.keys.map(Long::toString).toTypedArray())
                }
                try {
                    resolver.query(collection, arrayOf(BaseColumns._ID), args, null)?.use { cursor ->
                        while (cursor.moveToNext()) ids[cursor.getLong(0)]?.let(visible::add)
                    }
                } catch (_: SecurityException) {
                    // Keep the references if the user temporarily limits media access.
                } catch (_: IllegalArgumentException) {
                    // A removable media volume may have been disconnected.
                }
            }
        }
        return visible
    }

    private fun remember(entries: List<Entry>) {
        val edit = tracked.edit()
        entries.forEach { entry ->
            val data = JSONObject().put("name", entry.name).put("path", entry.path)
                .put("bytes", entry.bytes).put("added", entry.added)
            edit.putString(entry.uri.toString(), data.toString())
        }
        check(edit.commit()) { "Could not record the pending media move" }
    }

    private fun forget(entries: List<Entry>) {
        val edit = tracked.edit()
        entries.forEach { edit.remove(it.uri.toString()) }
        edit.apply()
    }
}
