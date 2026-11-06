package com.example.browser.gecko

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.text.format.Formatter
import androidx.compose.ui.platform.LocalContext

/** Download consent and a cancellable in-browser transfer status, without displaying request URLs. */
@Composable
internal fun GeckoDownloadUi(controller: GeckoDownloadController) {
    val context = LocalContext.current
    when (val state = controller.state) {
        is GeckoDownloadState.Offer -> AlertDialog(
            onDismissRequest = controller::reject,
            title = { Text("Download file?") },
            text = { Column { Text(state.name); state.length?.let { Text(Formatter.formatFileSize(context, it)) }; Text("Downloads stop if you close the browser and cannot resume.") } },
            confirmButton = { TextButton(onClick = controller::approve) { Text("Download") } },
            dismissButton = { TextButton(onClick = controller::reject) { Text("Cancel") } },
        )
        is GeckoDownloadState.Active -> Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(state.name, maxLines = 1)
                    Text("Downloaded ${Formatter.formatFileSize(context, state.bytes)}")
                }
                TextButton(onClick = controller::cancel) { Text("Cancel") }
            }
        }
        is GeckoDownloadState.Finished -> AlertDialog(
            onDismissRequest = controller::dismissResult,
            title = { Text(when (state.outcome) {
                is GeckoDownloadOutcome.Complete -> "Download complete"
                GeckoDownloadOutcome.Cancelled -> "Download cancelled"
                GeckoDownloadOutcome.Failed -> "Download failed"
            }) },
            text = { Text(if (state.outcome is GeckoDownloadOutcome.Complete) "${state.name} was saved in Downloads." else "${state.name} was not saved.") },
            confirmButton = { TextButton(onClick = controller::dismissResult) { Text("OK") } },
        )
        GeckoDownloadState.Idle -> Unit
    }
}
