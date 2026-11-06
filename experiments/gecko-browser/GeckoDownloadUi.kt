package com.example.browser.gecko

import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Consent, destination and cancellable transfer status without exposing authenticated URLs. */
@Composable
internal fun GeckoDownloadUi(controller: GeckoDownloadController) {
    val context = LocalContext.current
    val state = controller.state
    var requestedFor by remember { mutableStateOf<GeckoDownloadState.Offer?>(null) }
    var selectedTree by remember(state) { mutableStateOf<Uri?>(null) }
    var pickerFailed by remember(state) { mutableStateOf(false) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val expected = requestedFor
        requestedFor = null
        if (expected != null && controller.state === expected && uri?.scheme == "content") selectedTree = uri
    }
    when (state) {
        is GeckoDownloadState.Offer -> {
            AlertDialog(
                onDismissRequest = controller::reject,
                title = { Text("Download file?") },
                text = {
                    Column {
                        Text(state.name)
                        state.length?.let { Text(Formatter.formatFileSize(context, it)) }
                        Text(if (selectedTree != null) "Selected folder (a unique name will be used)" else if (Build.VERSION.SDK_INT >= 29) "Downloads" else "Choose a folder to save this file")
                        TextButton(onClick = {
                            requestedFor = state
                            runCatching { chooseFolder.launch(null) }.onFailure { requestedFor = null; pickerFailed = true }
                        }) { Text("Choose folder") }
                        if (selectedTree != null && Build.VERSION.SDK_INT >= 29) TextButton(onClick = { selectedTree = null }) { Text("Use Downloads folder") }
                        if (pickerFailed) Text("Could not open the folder picker.")
                        Text("Downloads stop if you close the browser and cannot resume.")
                    }
                },
                confirmButton = { TextButton(enabled = Build.VERSION.SDK_INT >= 29 || selectedTree != null, onClick = {
                    val tree = selectedTree
                    val chosenName = state.name
                    val override = tree?.let { uri -> { GeckoDocumentTreeDestination(context, uri, chosenName, state.mimeType) } }
                    controller.approve(chosenName, if (tree == null) "Downloads" else "selected folder", override)
                }) { Text("Download") } },
                dismissButton = { TextButton(onClick = controller::reject) { Text("Cancel") } },
            )
        }
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
            text = { Text(if (state.outcome is GeckoDownloadOutcome.Complete) "The file was saved in ${state.location}." else "The download could not be completed.") },
            confirmButton = { TextButton(onClick = controller::dismissResult) { Text("OK") } },
        )
        GeckoDownloadState.Idle -> Unit
    }
}
