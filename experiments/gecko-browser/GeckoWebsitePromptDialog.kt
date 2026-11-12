package com.example.browser.gecko

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.GeckoSession

/** Only the selected tab's prompt can cover its page; other tabs wait for selection. */
@Composable
internal fun GeckoWebsitePromptDialog(picker: GeckoFilePicker, activeSession: GeckoSession?, pageUrl: String) {
    val request = picker.websitePromptFor(activeSession) ?: return
    if (request.session !== activeSession) return
    val host = runCatching { android.net.Uri.parse(pageUrl).host }.getOrNull()?.take(200).orEmpty()
    GeckoWebsitePromptContent(request.message, request.isConfirm, host) { picker.answerWebsitePrompt(request, it) }
}

@Composable
internal fun GeckoWebsitePromptContent(message: String, isConfirm: Boolean, host: String, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(if (isConfirm) "Website confirmation" else "Website message") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            if (host.isNotBlank()) Text("From: $host")
            Text(message)
        } },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("OK") } },
        dismissButton = if (isConfirm) { { TextButton(onClick = { onAnswer(false) }) { Text("Cancel") } } } else null,
    )
}
