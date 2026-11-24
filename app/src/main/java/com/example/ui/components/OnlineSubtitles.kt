package com.example.ui.components

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.example.util.PublicSubtitleSearch
import kotlinx.coroutines.CancellationException

@Composable
internal fun OnlineSubtitles(videoTitle: String, load: (Uri) -> Unit) {
    var open by remember(videoTitle) { mutableStateOf(false) }
    if (!open) {
        TextButton(onClick = { open = true }) { Text("Find online subtitles · CC0") }
        return
    }
    val cache = LocalContext.current.cacheDir
    var title by remember { mutableStateOf(com.example.util.subtitleSearchTitle(videoTitle)) }
    var query by remember { mutableStateOf(title) }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<PublicSubtitleSearch.Match>()) }
    var choice by remember { mutableStateOf<PublicSubtitleSearch.Match?>(null) }
    LaunchedEffect(query, attempt, choice) {
        try {
            val selected = choice
            if (selected == null) {
                results = emptyList()
                if (query.isBlank()) {
                    status = "Enter the movie or series title."
                    return@LaunchedEffect
                }
                status = "Searching Internet Archive…"
                results = PublicSubtitleSearch.search(query)
                status = if (results.isEmpty()) "No CC0 captions found. Try a shorter title." else "Choose a caption file"
            } else {
                status = "Downloading subtitles…"
                val file = PublicSubtitleSearch.download(selected, cache)
                load(Uri.fromFile(file))
                open = false
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { status = "Could not finish. Tap Search to retry." }
    }
    OutlinedTextField(title, { title = it }, label = { Text("Title to search on Internet Archive") }, singleLine = true)
    TextButton(enabled = title.isNotBlank(), onClick = { query = title; choice = null; attempt++ }) { Text("Search") }
    Text(status)
    if (choice == null) results.forEach { match ->
        TextButton(onClick = { choice = match }) { Text("${match.title} · ${match.file}") }
    }
    TextButton(onClick = { open = false }) { Text("Close search") }
}
