package com.example.browser.gecko

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun GeckoContextActionsDialog(actions: GeckoContextActions, activeId: Int) {
    actions.request?.takeIf { it.tabId == activeId }?.let { request ->
        AlertDialog(
            onDismissRequest = actions::dismiss,
            title = { Text(if (request.image) "Image" else "Link") },
            text = { Column {
                if (request.privateTab) Text("Copying or sharing leaves private browsing.")
                TextButton(onClick = actions::open) { Text(if (request.privateTab) "Open in new private tab" else "Open in new tab") }
                TextButton(onClick = actions::copy) { Text(if (request.image) "Copy image address" else "Copy link") }
                TextButton(onClick = actions::share) { Text(if (request.image) "Share image address" else "Share link") }
            } },
            confirmButton = { TextButton(onClick = actions::dismiss) { Text("Cancel") } },
        )
    }
    if (actions.failed) AlertDialog(
        onDismissRequest = actions::dismissFailure,
        title = { Text("Could not complete action") },
        text = { Text("Check the tab limit or available apps and try again.") },
        confirmButton = { TextButton(onClick = actions::dismissFailure) { Text("OK") } },
    )
}
