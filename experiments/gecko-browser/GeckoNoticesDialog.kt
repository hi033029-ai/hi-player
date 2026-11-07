package com.example.browser.gecko

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GeckoNoticesDialog(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(app) {
        text = withContext(Dispatchers.IO) { runCatching { app.assets.open("gecko_component_licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault("Bundled notices could not be read. Gecko's own notices remain available from Browser options.") }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Open source notices") },
        text = { if (text == null) LinearProgressIndicator() else Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) { Text(text.orEmpty(), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
