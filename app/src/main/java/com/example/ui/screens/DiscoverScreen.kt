package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.model.DiscoverDetails
import com.example.model.DiscoverMediaType
import com.example.model.DiscoverTitle
import com.example.model.RemoteMediaMatch
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.DiscoverViewModel

@Composable
fun DiscoverScreen(
    discoverViewModel: DiscoverViewModel,
    onPlayRemote: (RemoteMediaMatch) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by discoverViewModel.uiState.collectAsState()
    val sources by discoverViewModel.sourceSettings.collectAsState()
    val tmdbConfigured by discoverViewModel.tmdbConfigured.collectAsState()
    val palette = LocalHiPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        if (state.selectedDetails == null) {
            DiscoverSearchHeader(
                query = state.query,
                loading = state.isLoading,
                onQueryChanged = discoverViewModel::setQuery,
                onSearch = discoverViewModel::searchTitles,
                onOpenSettings = onOpenSettings,
                tmdbConfigured = tmdbConfigured
            )
            state.errorMessage?.let { message ->
                DiscoverMessage(message = message, isError = true)
            }
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.primary)
                }
            } else if (state.results.isEmpty() && state.errorMessage == null) {
                DiscoverEmptyState(
                    sourcesConnected = sources.hasAnySource,
                    onOpenSettings = onOpenSettings
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp)
                ) {
                    item {
                        Text(
                            text = "TMDB RESULTS",
                            color = palette.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                    items(state.results, key = { "tmdb-${it.mediaType}-${it.tmdbId}" }) { title ->
                        DiscoverTitleRow(title = title, onClick = { discoverViewModel.selectTitle(title) })
                    }
                }
            }
        } else {
            DiscoverDetailsContent(
                details = state.selectedDetails!!,
                isSearchingSources = state.isSearchingSources,
                sourceMessage = state.sourceMessage,
                matches = state.sourceMatches,
                onBack = discoverViewModel::closeDetails,
                onSearchSources = { discoverViewModel.searchConnectedSources(state.selectedDetails!!.title) },
                onOpenSettings = onOpenSettings,
                onPlayRemote = { match ->
                    discoverViewModel.preparePlayback(match)
                    onPlayRemote(match)
                }
            )
        }
    }
}

@Composable
private fun DiscoverSearchHeader(
    query: String,
    loading: Boolean,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    tmdbConfigured: Boolean
) {
    val palette = LocalHiPalette.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Discover", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Find metadata, then play only from your sources", color = palette.textSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Discover source settings", tint = palette.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            label = { Text("Movie or TV series") },
            placeholder = { Text("e.g. Dune, The Last of Us") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.primary)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.surfaceBorder,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                focusedLabelColor = palette.primary,
                unfocusedLabelColor = palette.textSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = if (palette.isDark) Color.Black else Color.White)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Search TMDB")
            }

        }
    }
}

@Composable
private fun DiscoverEmptyState(sourcesConnected: Boolean, onOpenSettings: () -> Unit) {
    val palette = LocalHiPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text("Search movies and TV", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "TMDB supplies free title details. Hi Player searches only your connected Jellyfin or WebDAV media library for a playable copy.",
            color = palette.textSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = if (palette.isDark) Color.Black else Color.White)
        ) {
            Icon(if (sourcesConnected) Icons.Default.Settings else Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (sourcesConnected) "Manage sources" else "Connect a source")
        }
    }
}

@Composable
private fun DiscoverTitleRow(title: DiscoverTitle, onClick: () -> Unit) {
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (title.posterUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(title.posterUrl).crossfade(true).build(),
                    contentDescription = "${title.title} poster",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title.title, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                listOf(title.mediaType.label(), title.releaseYear, title.rating?.let { "★ %.1f".format(it) }).filterNotNull().joinToString(" • "),
                color = palette.primary,
                fontSize = 11.sp
            )
            if (title.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(title.overview, color = palette.textSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DiscoverDetailsContent(
    details: DiscoverDetails,
    isSearchingSources: Boolean,
    sourceMessage: String?,
    matches: List<RemoteMediaMatch>,
    onBack: () -> Unit,
    onSearchSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayRemote: (RemoteMediaMatch) -> Unit
) {
    val palette = LocalHiPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to results", tint = palette.textPrimary)
                }
                Text("Discover", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            DiscoverDetailsHeader(details)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AVAILABLE IN YOUR SOURCES", color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.7.sp)
                    sourceMessage?.let { Text(it, color = palette.textSecondary, fontSize = 11.sp) }
                }
                Button(
                    onClick = onSearchSources,
                    enabled = !isSearchingSources,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = if (palette.isDark) Color.Black else Color.White)
                ) { Text(if (isSearchingSources) "Searching…" else "Refresh") }
            }
            if (isSearchingSources) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Searching connected media libraries…", color = palette.textSecondary, fontSize = 12.sp)
                }
            }
            if (matches.isEmpty() && !isSearchingSources) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated, contentColor = palette.primary),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Connect or manage sources")
                }
            }
        }
        items(matches, key = { it.id }) { match ->
            RemoteMediaRow(match = match, onPlay = { onPlayRemote(match) })
        }
    }
}

@Composable
private fun DiscoverDetailsHeader(details: DiscoverDetails) {
    val title = details.title
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(width = 105.dp, height = 155.dp).clip(RoundedCornerShape(10.dp)).background(palette.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (title.posterUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(title.posterUrl).crossfade(true).build(),
                    contentDescription = "${title.title} poster",
                    modifier = Modifier.fillMaxSize()
                )
            } else Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(
                listOf(title.mediaType.label(), title.releaseYear, details.runtimeMinutes?.let { "$it min" }, title.rating?.let { "★ %.1f".format(it) }).filterNotNull().joinToString(" • "),
                color = palette.primary,
                fontSize = 12.sp
            )
            if (details.genres.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(details.genres.joinToString(" • "), color = palette.textSecondary, fontSize = 11.sp)
            }
            if (title.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(title.overview, color = palette.textSecondary, fontSize = 11.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (details.seasons.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            details.seasons.forEach { season ->
                Text(
                    text = "${season.name} (${season.episodeCount})",
                    color = palette.primary,
                    fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(palette.surfaceElevated).padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun RemoteMediaRow(match: RemoteMediaMatch, onPlay: () -> Unit) {
    val palette = LocalHiPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(palette.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play ${match.title}", tint = palette.primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(match.title, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            match.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val details = buildList {
                add(match.sourceName)
                if (match.is4k) add("4K")
                if (match.isHdr) add(match.hdrFormat ?: "HDR")
                match.audioLabels.take(3).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
            }.joinToString(" • ")
            Text(details, color = palette.primary, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (match.is4k || match.isHdr) {
            Icon(Icons.Default.HighQuality, contentDescription = "High quality", tint = palette.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DiscoverMessage(message: String, isError: Boolean) {
    val palette = LocalHiPalette.current
    Text(
        text = message,
        color = if (isError) Color(0xFFD14343) else palette.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

private fun DiscoverMediaType.label(): String = when (this) {
    DiscoverMediaType.MOVIE -> "Movie"
    DiscoverMediaType.TV -> "TV series"
}
