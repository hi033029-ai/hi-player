package com.example.browser.gecko

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.annotation.MainThread
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** One outstanding picker across all tabs; a canceled page cannot receive a late picker result. */
internal class GeckoFilePickerGate {
    private var next = 0L
    private var outstanding: Long? = null
    private var valid: Long? = null
    fun begin(): Long? {
        if (outstanding != null) return null
        val token = ++next
        outstanding = token
        valid = token
        return token
    }
    fun invalidate(token: Long) { if (valid == token) valid = null }
    fun finish(token: Long): Boolean {
        if (outstanding != token) return false
        outstanding = null
        val accepted = valid == token
        valid = null
        return accepted
    }
}

/** Normalizes web accept hints for Android's document picker. Unknown hints must not restrict selection. */
internal fun geckoPickerMimeTypes(hints: Array<out String>?, extensionType: (String) -> String?): List<String> {
    if (hints.isNullOrEmpty()) return emptyList()
    val result = mutableSetOf<String>()
    for (hint in hints) {
        val value = hint.trim().lowercase(java.util.Locale.ROOT)
        val mime = if (value.startsWith(".")) extensionType(value.drop(1)) else value
        if (mime.isNullOrBlank() || !mime.matches(Regex("[a-z0-9!#$&^_.+*-]+/[a-z0-9!#$&^_.+*-]+"))) return emptyList()
        if (mime == "*/*") return emptyList()
        result += mime
    }
    return result.toList().sorted()
}

/**
 * Wire [launch] to the host's Activity Result launcher. It must deliver the callback once,
 * including cancellations on the main thread; only one document picker is allowed at a time. The host owns this
 * delegate across tabs and calls close when the browser is destroyed. Not yet connected to UI.
 */
internal class GeckoFilePicker(
    context: Context,
    private val launch: (Intent, (List<Uri>?) -> Unit) -> Unit,
) : GeckoSession.PromptDelegate, AutoCloseable {
    private val appContext = context.applicationContext
    private val gate = GeckoFilePickerGate()
    private data class Pending(
        val token: Long,
        val prompt: GeckoSession.PromptDelegate.FilePrompt,
        val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
    )
    private var pending: Pending? = null
    private var closed = false

    @MainThread
    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        if (closed) return GeckoResult.fromValue(prompt.dismiss())
        val token = gate.begin() ?: return GeckoResult.fromValue(prompt.dismiss())
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        pending = Pending(token, prompt, result)
        prompt.setDelegate(object : GeckoSession.PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: GeckoSession.PromptDelegate.BasePrompt) {
                if (pending?.token == token) {
                    gate.invalidate(token)
                    pending = null
                    result.complete(null)
                }
            }
        })
        val folder = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER
        val intent = if (folder) Intent(Intent.ACTION_OPEN_DOCUMENT_TREE) else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val types = geckoPickerMimeTypes(prompt.mimeTypes) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            type = if (types.size == 1) types.single() else "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE)
        }
        try { launch(intent) { uris -> finish(token, uris) } }
        catch (_: Exception) { finish(token, null) }
        return result
    }

    @MainThread
    private fun finish(token: Long, uris: List<Uri>?) {
        if (!gate.finish(token)) return
        val request = pending?.takeIf { it.token == token } ?: return
        pending = null
        val supplied = uris.orEmpty()
        val files = supplied.distinct()
        val valid = files.isNotEmpty() && files.all { it.scheme == "content" } && (request.prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE || files.size == 1)
        val response = try {
            if (valid) request.prompt.confirm(appContext, files.toTypedArray()) else request.prompt.dismiss()
        } catch (_: Exception) {
            if (!request.prompt.isComplete) request.prompt.dismiss() else null
        }
        request.result.complete(response)
    }

    @MainThread override fun close() {
        if (closed) return
        closed = true
        pending?.let { request ->
            gate.invalidate(request.token)
            if (!request.prompt.isComplete) request.result.complete(request.prompt.dismiss())
            else request.result.complete(null)
        }
        pending = null
    }
}
