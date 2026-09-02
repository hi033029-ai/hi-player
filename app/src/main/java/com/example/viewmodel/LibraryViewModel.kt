package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaScanner
import com.example.data.PlayerPreferencesRepository
import com.example.db.HiPlayerDatabase
import com.example.db.PlaylistEntity
import com.example.db.VideoEntity
import com.example.model.VideoItem
import com.example.model.VideoResolutionBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class LibraryTab {
    ALL_VIDEOS,
    UHD_4K_REMUX,
    FOLDERS,
    HISTORY,
    FAVORITES
}

enum class LibraryViewMode {
    LAYER_LIST,   // Layer / List view
    GRADLE_GRID   // Gradle / Grid view
}

enum class VideoSortOption(val displayName: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Size (Largest)"),
    DURATION_DESC("Duration (Longest)"),
    QUALITY_DESC("Resolution (4K First)")
}

enum class LibraryNavMode(val displayName: String) {
    FOLDERS("Folders"),
    ALL_VIDEOS("All Videos"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    TREE_FOLDERS("Tree Folders"),
    ALL_FOLDERS("All Folders")
}

data class VideoFolder(
    val name: String,
    val videoCount: Int,
    val sampleVideo: VideoItem?
)

/**
 * One directory in the real nested folder tree, built from each video's
 * absolute file [VideoItem.path]. [dirPath] is the full directory path this
 * node represents (used as a stable navigation key); [directVideos] are the
 * videos that live directly inside this directory (not in a subfolder).
 */
data class TreeFolderNode(
    val name: String,
    val dirPath: String,
    val childFolders: List<TreeFolderNode>,
    val directVideos: List<VideoItem>,
    val totalVideoCount: Int
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = HiPlayerDatabase.getInstance(application)
    private val videoDao = database.videoDao()
    private val preferencesRepo = PlayerPreferencesRepository(application)
    private val libraryPrefs = application.getSharedPreferences("library_preferences", android.content.Context.MODE_PRIVATE)

    private val _allVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val allVideos = _allVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private var refreshJob: Job? = null

    private val _navMode = MutableStateFlow(
        runCatching { LibraryNavMode.valueOf(libraryPrefs.getString("nav_mode", LibraryNavMode.FOLDERS.name)!!) }
            .getOrDefault(LibraryNavMode.FOLDERS)
    )
    val navMode = _navMode.asStateFlow()

    private val _viewMode = MutableStateFlow(
        runCatching { LibraryViewMode.valueOf(libraryPrefs.getString("view_mode", LibraryViewMode.LAYER_LIST.name)!!) }
            .getOrDefault(LibraryViewMode.LAYER_LIST)
    )
    val viewMode = _viewMode.asStateFlow()

    private val _gridMinSize = MutableStateFlow(
        libraryPrefs.getInt("grid_min_size_dp", 160).coerceIn(110, 240)
    )
    val gridMinSize = _gridMinSize.asStateFlow()

    private val _sortOption = MutableStateFlow(VideoSortOption.DATE_DESC)
    val sortOption = _sortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder = _selectedFolder.asStateFlow()

    // Current directory being browsed in Tree Folders mode. Null = tree root
    // (shows every top-level directory that actually contains videos).
    private val _treeCurrentPath = MutableStateFlow<String?>(null)
    val treeCurrentPath = _treeCurrentPath.asStateFlow()

    val historyRecords = videoDao.getRecentHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    data class ContinueWatchingItem(
        val video: VideoItem,
        val lastPositionMs: Long,
        val durationMs: Long,
        val progressFraction: Float,
        val progressText: String
    )

    val continueWatchingVideos = combine(
        _allVideos,
        historyRecords
    ) { allVideos, history ->
        val videoMap = allVideos.associateBy { it.uri.toString() }
        history.mapNotNull { entity ->
            val baseVideo = videoMap[entity.uriString] ?: VideoItem(
                id = entity.uriString.hashCode().toLong(),
                uri = Uri.parse(entity.uriString),
                title = entity.title.ifBlank { "Video" },
                durationMs = entity.durationMs,
                sizeBytes = 0L,
                width = 1920,
                height = 1080,
                mimeType = "video/*",
                dateAdded = entity.lastWatchedTimestamp,
                folderName = "Storage",
                path = entity.uriString,
                codec = "HW Dec"
            )
            val duration = if (entity.durationMs > 0) entity.durationMs else baseVideo.durationMs
            val position = entity.lastPositionMs
            val fraction = if (duration > 0) {
                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val formatTime: (Long) -> String = { ms ->
                val totalSeconds = ms / 1000
                val hr = totalSeconds / 3600
                val min = (totalSeconds % 3600) / 60
                val sec = totalSeconds % 60
                if (hr > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", hr, min, sec)
                else String.format(java.util.Locale.US, "%02d:%02d", min, sec)
            }

            val progressText = if (duration > 0 && position > 0) {
                "${formatTime(position)} / ${formatTime(duration)}"
            } else if (duration > 0) {
                formatTime(duration)
            } else {
                "Resume"
            }

            ContinueWatchingItem(
                video = baseVideo.copy(isFavorite = entity.isFavorite),
                lastPositionMs = position,
                durationMs = duration,
                progressFraction = fraction,
                progressText = progressText
            )
        }.take(5)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteRecords = videoDao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlists = videoDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playerSettings = preferencesRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.AppPlayerSettings())

    // True only once the real persisted settings have been read from disk at least once.
    // Prevents the onboarding flow (welcome/theme/permissions) from flashing on every
    // cold start due to the StateFlow's placeholder default (isFirstLaunch = true)
    // being briefly visible before the actual DataStore value arrives.
    val isSettingsLoaded = preferencesRepo.settingsFlow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val filterCriteriaFlow = combine(_navMode, _searchQuery, _selectedFolder, _sortOption) { nav, query, folder, sort ->
        FilterCriteria(nav, query, folder, sort)
    }

    private data class FilterCriteria(
        val navMode: LibraryNavMode,
        val query: String,
        val folder: String?,
        val sort: VideoSortOption
    )

    private val historyAndFavFlow = combine(historyRecords, favoriteRecords) { history, favorites ->
        Pair(history, favorites)
    }

    val filteredVideos = combine(
        _allVideos,
        filterCriteriaFlow,
        historyAndFavFlow
    ) { videos: List<VideoItem>, criteria: FilterCriteria, historyFav: Pair<List<VideoEntity>, List<VideoEntity>> ->
        val (nav, query, folder, sort) = criteria
        val (history, favorites) = historyFav
        var list = videos

        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.folderName.contains(query, ignoreCase = true) ||
                        it.codec.contains(query, ignoreCase = true)
            }
        }

        list = when (nav) {
            LibraryNavMode.FOLDERS, LibraryNavMode.ALL_FOLDERS, LibraryNavMode.TREE_FOLDERS -> {
                if (folder != null) {
                    list.filter { it.folderName.equals(folder, ignoreCase = true) }
                } else {
                    list
                }
            }
            LibraryNavMode.ALL_VIDEOS -> {
                if (folder != null) {
                    list.filter { it.folderName.equals(folder, ignoreCase = true) }
                } else {
                    list
                }
            }
            LibraryNavMode.RECENT -> {
                if (folder != null) {
                    list.filter { it.folderName.equals(folder, ignoreCase = true) }
                } else {
                    list
                }
            }
            LibraryNavMode.FAVORITES -> {
                val favSet = favorites.map { it.uriString }.toSet()
                val favList = list.filter { favSet.contains(it.uri.toString()) }
                if (folder != null) {
                    favList.filter { it.folderName.equals(folder, ignoreCase = true) }
                } else {
                    favList
                }
            }
        }

        // Apply Sorting
        when (sort) {
            VideoSortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            VideoSortOption.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
            VideoSortOption.DATE_DESC -> list.sortedByDescending { it.id }
            VideoSortOption.DATE_ASC -> list.sortedBy { it.id }
            VideoSortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            VideoSortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
            VideoSortOption.QUALITY_DESC -> list.sortedWith(
                compareByDescending<VideoItem> { it.width * it.height }
                    .thenByDescending { it.isHdr }
                    .thenByDescending { it.sizeBytes }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders = _allVideos.map { videos ->
        videos.groupBy { it.folderName }
            .map { (name, group) ->
                VideoFolder(
                    name = name,
                    videoCount = group.size,
                    sampleVideo = group.firstOrNull()
                )
            }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        refreshVideos()
    }

    fun refreshVideos() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val scanned = MediaScanner.scanLocalVideos(getApplication())
                _allVideos.value = scanned
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setNavMode(mode: LibraryNavMode) {
        libraryPrefs.edit().putString("nav_mode", mode.name).apply()
        _navMode.value = mode
        _selectedFolder.value = null
        _treeCurrentPath.value = null
    }

    val treeRoot: kotlinx.coroutines.flow.StateFlow<TreeFolderNode?> = _allVideos
        .map { videos -> buildFolderTree(videos) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** The tree node currently being browsed (root when [treeCurrentPath] is null). */
    val currentTreeNode: kotlinx.coroutines.flow.StateFlow<TreeFolderNode?> = combine(
        treeRoot, _treeCurrentPath
    ) { root, path -> findTreeNode(root, path) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private fun buildFolderTree(videos: List<VideoItem>): TreeFolderNode? {
        if (videos.isEmpty()) return null

        class MutableNode(val name: String, val dirPath: String) {
            val children = linkedMapOf<String, MutableNode>()
            val directVideos = mutableListOf<VideoItem>()
        }

        val entries = videos.mapNotNull { video ->
            val dir = video.path.substringBeforeLast('/', missingDelimiterValue = "")
            if (dir.isBlank()) null else dir to video
        }
        if (entries.isEmpty()) return null

        val segmentLists = entries.map { (dir, _) -> dir.split('/').filter { it.isNotBlank() } }
        val minLen = segmentLists.minOf { it.size }
        var commonPrefixLen = 0
        outer@ while (commonPrefixLen < minLen) {
            val candidate = segmentLists[0][commonPrefixLen]
            for (segs in segmentLists) {
                if (segs[commonPrefixLen] != candidate) break@outer
            }
            commonPrefixLen++
        }

        val root = MutableNode(name = "Storage", dirPath = "")
        for ((dir, video) in entries) {
            val segments = dir.split('/').filter { it.isNotBlank() }.drop(commonPrefixLen)
            var current = root
            var pathAcc = ""
            for (segment in segments) {
                pathAcc = if (pathAcc.isEmpty()) segment else "$pathAcc/$segment"
                current = current.children.getOrPut(segment) { MutableNode(segment, pathAcc) }
            }
            current.directVideos.add(video)
        }

        fun freeze(node: MutableNode): TreeFolderNode {
            val children = node.children.values.map { freeze(it) }.sortedBy { it.name.lowercase() }
            val total = node.directVideos.size + children.sumOf { it.totalVideoCount }
            return TreeFolderNode(
                name = node.name,
                dirPath = node.dirPath,
                childFolders = children,
                directVideos = node.directVideos.sortedBy { it.title.lowercase() },
                totalVideoCount = total
            )
        }

        return freeze(root)
    }

    private fun findTreeNode(root: TreeFolderNode?, dirPath: String?): TreeFolderNode? {
        if (root == null) return null
        if (dirPath.isNullOrBlank()) return root
        var current: TreeFolderNode = root
        for (segment in dirPath.split('/').filter { it.isNotBlank() }) {
            val next = current.childFolders.find { it.name == segment } ?: return null
            current = next
        }
        return current
    }

    fun navigateTreeInto(dirPath: String) {
        _treeCurrentPath.value = dirPath
    }

    /** Moves up one directory level. Returns false if already at the tree root. */
    fun navigateTreeUp(): Boolean {
        val current = _treeCurrentPath.value ?: return false
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        _treeCurrentPath.value = parent.ifBlank { null }
        return true
    }

        fun toggleViewMode() {
        setViewMode(if (_viewMode.value == LibraryViewMode.LAYER_LIST) LibraryViewMode.GRADLE_GRID else LibraryViewMode.LAYER_LIST)
    }
    fun setViewMode(mode: LibraryViewMode) {
        libraryPrefs.edit().putString("view_mode", mode.name).apply()
        _viewMode.value = mode
    }

    fun setGridMinSize(sizeDp: Int) {
        val clamped = sizeDp.coerceIn(110, 240)
        libraryPrefs.edit().putInt("grid_min_size_dp", clamped).apply()
        _gridMinSize.value = clamped
    }

    fun setSortOption(option: VideoSortOption) {
        _sortOption.value = option
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    fun toggleFavorite(video: VideoItem, isFavorite: Boolean) {
        viewModelScope.launch {
            val record = videoDao.getVideoRecord(video.uri.toString())
            if (record != null) {
                videoDao.updateFavorite(video.uri.toString(), !isFavorite)
            } else {
                videoDao.insertOrUpdate(
                    VideoEntity(
                        uriString = video.uri.toString(),
                        title = video.title,
                        durationMs = video.durationMs,
                        isFavorite = true
                    )
                )
            }
        }
    }

    fun addExternalVideo(uri: Uri, onReady: (VideoItem) -> Unit) {
        viewModelScope.launch {
            val item = MediaScanner.createVideoItemFromUri(getApplication(), uri)
            _allVideos.value = listOf(item) + _allVideos.value.filter { it.uri != uri }
            onReady(item)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                videoDao.insertPlaylist(PlaylistEntity(name = name.trim()))
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            videoDao.clearHistory()
        }
    }

    fun setThemeMode(mode: com.example.data.AppThemeMode) {
        viewModelScope.launch {
            preferencesRepo.setThemeMode(mode)
        }
    }

    fun setFirstLaunchCompleted() {
        viewModelScope.launch {
            preferencesRepo.setFirstLaunchCompleted()
        }
    }

    fun setHwAccelerationMode(mode: com.example.data.HwAccelerationMode) {
        viewModelScope.launch {
            preferencesRepo.setHwAccelerationMode(mode)
        }
    }

    fun setWideColorGamut(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setWideColorGamut(enabled)
        }
    }

    fun setHdrEnhance(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setHdrEnhance(enabled)
        }
    }

    fun setRemuxUltraBufferMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setRemuxUltraBufferMode(enabled)
        }
    }

    fun setBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setBackgroundPlay(enabled)
        }
    }

    fun setAutoPip(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setAutoPip(enabled)
        }
    }

    fun setUiTextSize(sizeSp: Int) {
        viewModelScope.launch {
            preferencesRepo.setUiTextSize(sizeSp)
        }
    }
}
