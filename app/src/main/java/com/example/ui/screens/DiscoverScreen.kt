package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R
import com.example.model.DiscoverCastMember
import com.example.model.DiscoverDetails
import com.example.model.DiscoverHomeSection
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by discoverViewModel.uiState.collectAsState()
    val sources by discoverViewModel.sourceSettings.collectAsState()
    val palette = LocalHiPalette.current
    var showSourceChooser by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val connectedSourceCount = listOf(
        sources.googleDrive?.isConfigured == true,
        sources.jellyfin?.isConfigured == true,
        sources.webDav?.isConfigured == true
    ).count { it }

    // Discover is opened above a library tab rather than being a root Android
    // destination. Back therefore closes title details first, then restores
    // the tab the user was viewing instead of allowing Android to finish the
    // activity.
    BackHandler(enabled = !showSourceChooser) {
        if (state.selectedDetails != null) {
            discoverViewModel.closeDetails()
        } else {
            onBack()
        }
    }

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
            )
            state.errorMessage?.let { message ->
                DiscoverMessage(message = message, isError = true)
            }
            if (state.isLoading || state.isLoadingHome || state.isBrowsingGoogleDrive) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.primary)
                }
            } else if (state.googleDriveFiles.isNotEmpty() || state.googleDriveMessage != null) {
                GoogleDriveFilesContent(
                    files = state.googleDriveFiles,
                    message = state.googleDriveMessage,
                    modifier = Modifier.weight(1f),
                    onPlay = { match ->
                        discoverViewModel.preparePlayback(match)
                        onPlayRemote(match)
                    }
                )
            } else if (state.results.isNotEmpty()) {
                SearchResultsContent(
                    results = state.results,
                    modifier = Modifier.weight(1f),
                    onTitleSelected = discoverViewModel::selectTitle,
                )
            } else if (state.homeSections.isNotEmpty()) {
                TmdbHomeContent(
                    sections = state.homeSections,
                    googleDriveConnected = sources.googleDrive?.isConfigured == true,
                    sourcesConnected = sources.hasAnySource,
                    modifier = Modifier.weight(1f),
                    onTitleSelected = discoverViewModel::selectTitle,
                    onBrowseDrive = discoverViewModel::browseGoogleDrive,
                    onConnectPersonalMedia = { showSourceChooser = true },
                )
            } else {
                DiscoverEmptyState(
                    googleDriveConnected = sources.googleDrive?.isConfigured == true,
                    sourcesConnected = sources.hasAnySource,
                    modifier = Modifier.weight(1f),
                    onBrowseDrive = discoverViewModel::browseGoogleDrive,
                    onConnectPersonalMedia = { showSourceChooser = true }
                )
            }
        } else {
            DiscoverDetailsContent(
                details = state.selectedDetails!!,
                isSearchingSources = state.isSearchingSources,
                sourceMessage = state.sourceMessage,
                matches = state.sourceMatches,
                hasConnectedSources = sources.hasAnySource,
                onBack = discoverViewModel::closeDetails,
                onSearchSources = { discoverViewModel.searchConnectedSources(state.selectedDetails!!.title) },
                onShowSourceChooser = { showSourceChooser = true },
                onPlayRemote = { match ->
                    discoverViewModel.preparePlayback(match)
                    onPlayRemote(match)
                }
            )
        }
    }
    if (showSourceChooser) {
        DiscoverSourceChooser(
            connectedSourceCount = connectedSourceCount,
            onDismiss = { showSourceChooser = false },
            onOpenSettings = {
                showSourceChooser = false
                onOpenSettings()
            }
        )
    }
}

@Composable
private fun DiscoverSearchHeader(
    query: String,
    loading: Boolean,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Discover", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Find a title, then play it from your own media", color = palette.textSecondary, fontSize = 11.sp)
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SearchResultsContent(
    results: List<DiscoverTitle>,
    modifier: Modifier = Modifier,
    onTitleSelected: (DiscoverTitle) -> Unit,
) {
    val palette = LocalHiPalette.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
    ) {
        item {
            Text(
                text = "SEARCH RESULTS",
                color = palette.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
        items(results, key = { "catalog-${it.catalogSource}-${it.mediaType}-${it.catalogId}" }) { title ->
            DiscoverTitleRow(title = title, onClick = { onTitleSelected(title) })
        }
        item { TmdbAttribution(modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun TmdbHomeContent(
    sections: List<DiscoverHomeSection>,
    googleDriveConnected: Boolean,
    sourcesConnected: Boolean,
    modifier: Modifier = Modifier,
    onTitleSelected: (DiscoverTitle) -> Unit,
    onBrowseDrive: () -> Unit,
    onConnectPersonalMedia: () -> Unit,
) {
    val palette = LocalHiPalette.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
    ) {
        item {
            Text(
                text = "TMDB DISCOVER",
                color = palette.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
        items(sections, key = { it.id }) { section ->
            Column(modifier = Modifier.padding(bottom = 18.dp)) {
                Text(
                    text = section.title,
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                ) {
                    items(
                        items = section.titles,
                        key = { "${section.id}-${it.mediaType}-${it.catalogId}" },
                    ) { title ->
                        TmdbHomePosterCard(title = title, onClick = { onTitleSelected(title) })
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text(
                    text = "YOUR MEDIA",
                    color = palette.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "TMDB provides information only. Play a matching file from media you connect.",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = if (googleDriveConnected) onBrowseDrive else onConnectPersonalMedia,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.surfaceElevated,
                        contentColor = palette.primary,
                    ),
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            googleDriveConnected -> "Browse Google Drive videos"
                            sourcesConnected -> "Manage personal media"
                            else -> "Connect personal media"
                        },
                    )
                }
            }
            TmdbAttribution(modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun TmdbHomePosterCard(title: DiscoverTitle, onClick: () -> Unit) {
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = 116.dp, height = 172.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (title.posterUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(title.posterUrl).crossfade(true).build(),
                    contentDescription = "${title.title} poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title.title,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOf(title.releaseYear, title.rating?.let { "★ %.1f".format(it) })
                .filterNotNull()
                .joinToString("  •  "),
            color = palette.primary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TmdbAttribution(modifier: Modifier = Modifier) {
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.tmdb_attribution_logo),
            contentDescription = "The Movie Database",
            modifier = Modifier.width(132.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.",
            color = palette.textSecondary,
            fontSize = 9.sp,
            lineHeight = 12.sp,
        )
    }
}

@Composable
private fun GoogleDriveFilesContent(
    files: List<RemoteMediaMatch>,
    message: String?,
    modifier: Modifier = Modifier,
    onPlay: (RemoteMediaMatch) -> Unit
) {
    val palette = LocalHiPalette.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp)
    ) {
        item {
            Text(
                text = "GOOGLE DRIVE VIDEOS",
                color = palette.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )
            message?.let {
                Text(
                    text = it,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 0.dp)
                )
            }
        }
        items(files, key = { it.id }) { match ->
            RemoteMediaRow(match = match, onPlay = { onPlay(match) })
        }
    }
}

@Composable
private fun DiscoverEmptyState(
    googleDriveConnected: Boolean,
    sourcesConnected: Boolean,
    modifier: Modifier = Modifier,
    onBrowseDrive: () -> Unit,
    onConnectPersonalMedia: () -> Unit,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
            Text("Search movies and TV", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Search a title, then choose a matching copy from media you have connected.",
                color = palette.textSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = if (googleDriveConnected) onBrowseDrive else onConnectPersonalMedia,
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = if (palette.isDark) Color.Black else Color.White)
        ) {
            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    googleDriveConnected -> "Browse Google Drive videos"
                    sourcesConnected -> "Connect or manage personal media"
                    else -> "Connect personal media"
                }
            )
        }
    }
}

@Composable
private fun DiscoverSourceChooser(
    connectedSourceCount: Int,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalHiPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        title = { Text("Choose a media source", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    if (connectedSourceCount == 0) {
                        "Connect only personal libraries or folders you are allowed to access."
                    } else {
                        "$connectedSourceCount source${if (connectedSourceCount == 1) " is" else "s are"} already connected."
                    },
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                SourceChooserOption(
                    icon = Icons.Default.CloudDone,
                    title = "Google Drive",
                    detail = "Choose your Google account and a video folder",
                    onClick = onOpenSettings
                )
                SourceChooserOption(
                    icon = Icons.Default.Explore,
                    title = "Jellyfin",
                    detail = "Connect a free Jellyfin media library you manage",
                    onClick = onOpenSettings
                )
                SourceChooserOption(
                    icon = Icons.Default.Storage,
                    title = "WebDAV",
                    detail = "Connect a folder you control",
                    onClick = onOpenSettings
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close", color = palette.primary)
            }
        }
    )
}

@Composable
private fun SourceChooserOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    val palette = LocalHiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = palette.textSecondary, fontSize = 10.5.sp)
        }
        Text("Connect", color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
    hasConnectedSources: Boolean,
    onBack: () -> Unit,
    onSearchSources: () -> Unit,
    onShowSourceChooser: () -> Unit,
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
            if (details.cast.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "CAST & CHARACTERS",
                    color = palette.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                ) {
                    items(details.cast, key = { it.id }) { member ->
                        DiscoverCastCard(member)
                    }
                }
            }
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
            if (matches.isEmpty() && !isSearchingSources && !hasConnectedSources) {
                Button(
                    onClick = onShowSourceChooser,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated, contentColor = palette.primary),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Connect personal media")
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
private fun DiscoverCastCard(member: DiscoverCastMember) {
    val palette = LocalHiPalette.current
    val context = LocalContext.current
    Column(modifier = Modifier.width(88.dp)) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(palette.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (member.profileUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(member.profileUrl).crossfade(true).build(),
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.Explore, contentDescription = null, tint = palette.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = member.name,
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        member.character?.let { character ->
            Text(
                text = character,
                color = palette.textSecondary,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
