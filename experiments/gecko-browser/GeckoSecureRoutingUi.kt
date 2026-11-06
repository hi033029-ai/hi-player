package com.example.browser.gecko

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GeckoSecureRoutingDialogs(routing: GeckoSecureRouting, activeId: Int) {
    routing.pending?.takeIf { it.tabId == activeId }?.let { request ->
        AlertDialog(
            onDismissRequest = routing::dismiss,
            title = { Text(if (request.external) "Open another app?" else "Website sign-in") },
            text = { Text(if (request.external) "Open this link in another app?" else if (request.isPrivate)
                "Continue in your device browser to sign in. That browser may save your activity and account even though this tab is private. Your Hi Player account is separate."
                else "Continue in your device browser to sign in. Your website session and Hi Player account are separate.") },
            confirmButton = { TextButton(onClick = routing::confirm) { Text("Continue") } },
            dismissButton = { TextButton(onClick = routing::dismiss) { Text("Cancel") } },
        )
    }
    if (routing.failure) AlertDialog(
        onDismissRequest = routing::dismissFailure,
        title = { Text("Could not open link") },
        text = { Text("Install or update a compatible browser or app, then try again.") },
        confirmButton = { TextButton(onClick = routing::dismissFailure) { Text("OK") } },
    )
}

@Composable
internal fun GeckoSecureSitePage(routing: GeckoSecureRouting, tabId: Int, onUseHiBrowser: (String) -> Unit) {
    val url = routing.route(tabId) ?: return
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("This website is open in your device browser.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text("Your website sign-in stays in that browser and is separate from Hi Player.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { routing.continueWebsite(tabId) }) { Text("Continue website") }
        TextButton(onClick = { routing.useHiBrowser(tabId); onUseHiBrowser(url) }) { Text("Use Hi Browser instead") }
    }
}
