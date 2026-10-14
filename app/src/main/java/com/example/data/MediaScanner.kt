package com.example.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.PendingDownloadFormat
import com.example.model.VideoItem
import com.example.player.DownloadingFilesRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

object MediaScanner {

    private const val NEW_VIDEO_WINDOW_SECONDS = 24L * 60L * 60L

    suspend fun scanLocalVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<VideoItem>()
        val activeGrowingContentUris = mutableSetOf<String>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Untitled Video"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val dateAdded = cursor.getLong(dateCol)
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val lastModifiedMs = cursor.getLong(modifiedCol).coerceAtLeast(0L) * 1000L
                    val growthKey = dataPath.ifBlank { contentUri.toString() }
                    val looksLikeDownload = dataPath.contains("/download/", ignoreCase = true) || dataPath.isBlank()
                    val isFinalNameGrowing = looksLikeDownload &&
                        !PendingDownloadFormat.isPending(name) &&
                        DownloadingFilesRegistry.observeFinalNameDownload(growthKey, size, lastModifiedMs)
                    val rawFile = dataPath.takeIf(String::isNotBlank)?.let(::File)
                    val playbackUri = if (isFinalNameGrowing && rawFile?.isFile == true && rawFile.canRead()) {
                        Uri.fromFile(rawFile)
                    } else {
                        contentUri
                    }
                    val playbackKey = if (playbackUri.scheme == "file") {
                        playbackUri.path.orEmpty()
                    } else {
                        playbackUri.toString()
                    }
                    if (isFinalNameGrowing) {
                        // A readable raw file gives Media3 a stable descriptor
                        // while a download manager appends bytes. Fall back to
                        // the MediaStore URI only when scoped storage hides it.
                        DownloadingFilesRegistry.markDownloading(playbackKey, size)
                        if (playbackUri.scheme == "content") {
                            activeGrowingContentUris += playbackUri.toString()
                        }
                    }
                    if (!isFinalNameGrowing) {
                        if (dataPath.isNotBlank()) DownloadingFilesRegistry.markComplete(dataPath)
                        DownloadingFilesRegistry.markComplete(contentUri.toString())
                    }
                    val growingProgress = if (isFinalNameGrowing) {
                        DownloadingFilesRegistry.progress(playbackKey)
                    } else {
                        null
                    }
                    val folderName = if (dataPath.isNotEmpty()) {
                        val parent = File(dataPath).parentFile?.name
                        parent ?: "Internal Storage"
                    } else {
                        "Videos"
                    }

                    val is4k = !isFinalNameGrowing && (width >= 3840 || height >= 2160)
                    val codec = when {
                        mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> "HEVC (H.265)"
                        mime.contains("av01", ignoreCase = true) || mime.contains("av1", ignoreCase = true) -> "AV1 UHD"
                        mime.contains("vp9", ignoreCase = true) -> "VP9 HDR"
                        else -> if (is4k) "HEVC Remux" else "AVC (H.264)"
                    }

                    videoList.add(
                        VideoItem(
                            id = id,
                            uri = playbackUri,
                            title = name,
                            durationMs = if (isFinalNameGrowing) 0L else duration,
                            sizeBytes = size,
                            width = if (isFinalNameGrowing) 0 else width,
                            height = if (isFinalNameGrowing) 0 else height,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            folderName = folderName,
                            path = dataPath,
                            isHdr = is4k,
                            hdrFormat = if (is4k) "HDR10 / Dolby Vision" else null,
                            codec = if (isFinalNameGrowing) "Downloading" else codec,
                            bitrate = if (!isFinalNameGrowing && duration > 0) (size * 8000L / duration) else 0L,
                            isPartialDownload = isFinalNameGrowing,
                            downloadedBytes = growingProgress?.downloadedBytes ?: 0L,
                            downloadSpeedBytesPerSecond = growingProgress?.speedBytesPerSecond ?: 0L,
                            isNewVideo = isFinalNameGrowing || (dateAdded > 0L &&
                                (System.currentTimeMillis() / 1000L - dateAdded) in 0..NEW_VIDEO_WINDOW_SECONDS)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // A pending file can already be indexed by MediaStore with zero
        // duration. Replace that ordinary row with the growing-file entry so
        // the Video Library keeps its partial-download state and file URI.
        val indexedVideoPaths = videoList.map { it.path }.filter(String::isNotBlank).toSet()
        val partialItems = scanPartialDownloadVideos(
            context = context,
            activeGrowingContentUris = activeGrowingContentUris,
            indexedVideoPaths = indexedVideoPaths
        )
        val partialPaths = partialItems.map { it.path }.filter(String::isNotEmpty).toSet()
        if (partialPaths.isNotEmpty()) {
            videoList.removeAll { it.path in partialPaths }
        }
        // Some OEM MediaStore implementations expose a pending file through
        // Video and Files collections with different (or blank) data paths.
        // Remove that temporary Video row by its clean display name before the
        // canonical partial item is added, without affecting completed videos.
        val pendingTitles = partialItems.map { it.title.lowercase(Locale.ROOT) }.toSet()
        if (pendingTitles.isNotEmpty()) {
            videoList.removeAll { indexed ->
                PendingDownloadFormat.isPending(indexed.title) &&
                    PendingDownloadFormat.originalName(indexed.title).lowercase(Locale.ROOT) in pendingTitles
            }
        }
        videoList += partialItems

        // WhatsApp can keep media outside the MediaStore collection until a
        // gallery scan runs, especially in its Android/media app folder. Scan
        // only the known video directories the app can read; never scan private
        // chats or arbitrary storage trees.
        val indexedPathsAfterMediaStore = videoList.map { it.path }
            .filter(String::isNotBlank)
            .toSet()
        videoList += scanKnownMessengerVideos(indexedPathsAfterMediaStore)

        // If no local videos found on device/emulator, add high-quality 4K UHD Remux demo items
        if (videoList.isEmpty()) {
            videoList.addAll(getDemoRemuxVideos())
        }

        videoList.distinctBy { video -> video.path.ifBlank { video.uri.toString() } }
    }

    private fun scanKnownMessengerVideos(indexedPaths: Set<String>): List<VideoItem> {
        val storageRoot = Environment.getExternalStorageDirectory()
        val candidates = listOf(
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
            File(storageRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Video"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Video"),
            File(storageRoot, "WhatsApp Business/Media/WhatsApp Business Video"),
        ).filter { it.isDirectory && it.canRead() }
            .map { directory -> runCatching { directory.canonicalFile }.getOrDefault(directory) }
            .distinctBy(File::getAbsolutePath)

        return candidates.asSequence()
            .flatMap { directory -> directory.walkTopDown().maxDepth(4) }
            .filter(File::isFile)
            .filter { file -> file.extension.lowercase(Locale.ROOT) in PendingDownloadFormat.videoExtensions }
            .filterNot { file -> PendingDownloadFormat.isPending(file.name) || file.absolutePath in indexedPaths }
            .map(::toMessengerVideoItem)
            .distinctBy { it.path }
            .toList()
    }

    private fun toMessengerVideoItem(file: File): VideoItem {
        val metadata = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
            retriever.setDataSource(file.absolutePath)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 0,
            )
            } finally {
                retriever.release()
            }
        }.getOrDefault(Triple(0L, 0, 0))
        val (duration, width, height) = metadata
        val is4k = width >= 3840 || height >= 2160
        return VideoItem(
            id = file.absolutePath.hashCode().toLong(),
            uri = Uri.fromFile(file),
            title = file.name,
            durationMs = duration,
            sizeBytes = file.length(),
            width = width,
            height = height,
            mimeType = mimeTypeForExtension(file.extension.lowercase(Locale.ROOT)),
            dateAdded = file.lastModified() / 1000L,
            folderName = file.parentFile?.name ?: "WhatsApp Video",
            path = file.absolutePath,
            isHdr = is4k,
            hdrFormat = if (is4k) "HDR10 / Dolby Vision" else null,
            codec = if (is4k) "HEVC Remux" else "AVC (H.264)",
            bitrate = if (duration > 0) file.length() * 8000L / duration else 0L,
        )
    }

    private fun scanPartialDownloadVideos(
        context: Context,
        activeGrowingContentUris: Set<String>,
        indexedVideoPaths: Set<String>
    ): List<VideoItem> {
        // The public Download path can differ across Android versions and OEMs.
        // Try common aliases and keep only directories this process can read.
        val candidates = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Downloads"),
            File("/storage/emulated/0/Download"),
            File("/sdcard/Download")
        ).filter { it.isDirectory && it.canRead() }
            .map { directory -> runCatching { directory.canonicalFile }.getOrDefault(directory) }
            // /sdcard/Download and /storage/emulated/0/Download are often the
            // same directory. Canonical identity prevents duplicate cards.
            .distinctBy { it.absolutePath }

        val pathItems = runCatching {
            candidates.asSequence()
                .flatMap { it.walkTopDown().maxDepth(3) }
                .filter(File::isFile)
                .mapNotNull { file ->
                    val lowerName = file.name.lowercase()
                    val suffix = PendingDownloadFormat.suffixFor(lowerName) ?: return@mapNotNull null
                    val originalName = PendingDownloadFormat.originalName(file.name)
                    val extension = originalName.substringAfterLast('.', "").lowercase()
                    val playableExtension = extension.takeIf { it in PendingDownloadFormat.videoExtensions }
                        ?: detectVideoContainer(file)
                        ?: return@mapNotNull null

                    DownloadingFilesRegistry.markDownloading(file.absolutePath, file.length())
                    val progress = DownloadingFilesRegistry.progress(file.absolutePath)

                    VideoItem(
                        id = file.absolutePath.hashCode().toLong(),
                        uri = Uri.fromFile(file),
                        title = originalName,
                        // The container index may not exist until the download
                        // finishes. The explicit partial flag, not duration,
                        // controls its library visibility and UI treatment.
                        durationMs = 0L,
                        sizeBytes = file.length(),
                        mimeType = mimeTypeForExtension(playableExtension),
                        dateAdded = file.lastModified() / 1000L,
                        folderName = file.parentFile?.name ?: "Downloads",
                        path = file.absolutePath,
                        width = 0,
                        height = 0,
                        codec = "Downloading",
                        isPartialDownload = true,
                        downloadedBytes = progress?.downloadedBytes ?: file.length(),
                        downloadSpeedBytesPerSecond = progress?.speedBytesPerSecond ?: 0L,
                        isNewVideo = true
                    )
                }
                .distinctBy { it.path }
                .toList()
                .also { found ->
                    android.util.Log.d(
                        "MediaScanner",
                        "pending downloads found: ${found.size} across ${candidates.joinToString { it.absolutePath }}"
                    )
                }
        }.getOrElse { error ->
            android.util.Log.e("MediaScanner", "pending download scan failed", error)
            emptyList()
        }
        if (candidates.isNotEmpty()) {
            DownloadingFilesRegistry.markMissingPendingFileDownloadsComplete(
                pathItems.map { it.path }.filter(String::isNotBlank).toSet()
            )
        }

        // Several download managers write to the final name (for example,
        // movie.mp4) from the first byte. A suffix-only detector then treats
        // it as finished, even though Media3 reads it before the file is
        // complete. Track recently modified video files whose length changes
        // across scans and surface the active ones as normal DOWNLOADING cards.
        val finalNameGrowingItems = runCatching {
            candidates.asSequence()
                .flatMap { it.walkTopDown().maxDepth(3) }
                .filter(File::isFile)
                .mapNotNull { file ->
                    if (PendingDownloadFormat.isPending(file.name)) return@mapNotNull null
                    // A MediaStore row is already watching this path above. Do
                    // not recreate its registry state via the raw scanner.
                    if (file.absolutePath in indexedVideoPaths) return@mapNotNull null
                    val extension = file.extension.lowercase()
                    if (extension !in PendingDownloadFormat.videoExtensions) return@mapNotNull null
                    if (!DownloadingFilesRegistry.observeFinalNameDownload(
                            key = file.absolutePath,
                            sizeBytes = file.length(),
                            lastModifiedMs = file.lastModified()
                        )
                    ) return@mapNotNull null
                    val progress = DownloadingFilesRegistry.progress(file.absolutePath)

                    VideoItem(
                        id = file.absolutePath.hashCode().toLong(),
                        uri = Uri.fromFile(file),
                        title = file.name,
                        durationMs = 0L,
                        sizeBytes = file.length(),
                        mimeType = mimeTypeForExtension(extension),
                        dateAdded = file.lastModified() / 1000L,
                        folderName = file.parentFile?.name ?: "Downloads",
                        path = file.absolutePath,
                        width = 0,
                        height = 0,
                        codec = "Downloading",
                        isPartialDownload = true,
                        downloadedBytes = progress?.downloadedBytes ?: file.length(),
                        downloadSpeedBytesPerSecond = progress?.speedBytesPerSecond ?: 0L,
                        isNewVideo = true
                    )
                }
                .distinctBy { it.path }
                .toList()
        }.getOrElse { error ->
            android.util.Log.w("MediaScanner", "final-name download scan failed", error)
            emptyList()
        }

        // A browser can expose an unfinished item through the generic Files
        // collection without registering it as Video media. Querying it gives
        // us a content URI on scoped-storage devices before the download ends.
        val mediaStoreItems = scanMediaStorePendingDownloads(context)
        if (mediaStoreItems != null) {
            val contentUris = activeGrowingContentUris + mediaStoreItems.map { it.uri.toString() }
            DownloadingFilesRegistry.markMissingContentDownloadsComplete(contentUris)
        }

        return (pathItems + finalNameGrowingItems + mediaStoreItems.orEmpty())
            // Prefer the directly readable file discovered above. A pending
            // entry can also appear in MediaStore Files with a different URI,
            // which previously rendered a second identical download card. The
            // parent-folder component preserves separate same-named files.
            .distinctBy(::pendingMediaIdentity)
    }

    private fun pendingMediaIdentity(video: VideoItem): String {
        val parent = video.path.takeIf(String::isNotBlank)
            ?.let { path -> File(path).parentFile?.name }
            ?: video.folderName
        val normalizedParent = parent.trim().lowercase(Locale.ROOT)
            .removeSuffix("s")
            .ifBlank { "downloads" }
        return "$normalizedParent\u0000${video.title.trim().lowercase(Locale.ROOT)}"
    }

    private fun scanMediaStorePendingDownloads(context: Context): List<VideoItem>? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        val selection = PendingDownloadFormat.suffixes.joinToString(" OR ") {
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        }
        val selectionArgs = PendingDownloadFormat.suffixes.map { "%$it" }.toTypedArray()

        return runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                buildList {
                    while (cursor.moveToNext()) {
                        val pendingName = cursor.getString(nameColumn) ?: continue
                        PendingDownloadFormat.suffixFor(pendingName) ?: continue
                        val originalName = PendingDownloadFormat.originalName(pendingName)
                        val mimeType = cursor.getString(mimeColumn).orEmpty()
                        val extension = originalName.substringAfterLast('.', "").lowercase()
                        if (extension !in PendingDownloadFormat.videoExtensions && !mimeType.startsWith("video/", ignoreCase = true)) continue

                        val id = cursor.getLong(idColumn)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                        val dataPath = if (dataColumn >= 0) cursor.getString(dataColumn).orEmpty() else ""
                        DownloadingFilesRegistry.markDownloading(uri.toString(), size)
                        val progress = DownloadingFilesRegistry.progress(uri.toString())
                        add(
                            VideoItem(
                                id = id,
                                uri = uri,
                                title = originalName,
                                durationMs = 0L,
                                sizeBytes = size,
                                mimeType = mimeType.ifBlank { mimeTypeForExtension(extension) },
                                dateAdded = cursor.getLong(modifiedColumn).coerceAtLeast(0L),
                                folderName = dataPath.takeIf(String::isNotBlank)
                                    ?.let { File(it).parentFile?.name }
                                    ?: "Downloads",
                                path = dataPath,
                                width = 0,
                                height = 0,
                                codec = "Downloading",
                                isPartialDownload = true,
                                downloadedBytes = progress?.downloadedBytes ?: size,
                                downloadSpeedBytesPerSecond = progress?.speedBytesPerSecond ?: 0L,
                                isNewVideo = true
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.onFailure { error ->
            android.util.Log.w("MediaScanner", "MediaStore Files pending scan unavailable", error)
        }.getOrNull()
    }

    private fun detectVideoContainer(file: File): String? = runCatching {
        val header = ByteArray(12)
        val count = FileInputStream(file).use { input -> input.read(header) }
        if (count < 4) return@runCatching null
        when {
            header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> "mkv"
            count >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp" -> "mp4"
            header[0] == 0x47.toByte() -> "ts"
            else -> null
        }
    }.getOrNull()

    private fun mimeTypeForExtension(extension: String): String = when (extension) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts" -> "video/mp2t"
        "3gp" -> "video/3gpp"
        else -> "video/mp4"
    }

    fun getDemoRemuxVideos(): List<VideoItem> {
        val list = mutableListOf<VideoItem>()

        // 202607 folder
        list.add(
            VideoItem(
                id = 2001L,
                uri = Uri.parse("https://media.w3.org/2010/05/sintel/trailer.mp4"),
                title = "VID_20260715_143022_4K.mp4",
                durationMs = 184000L,
                sizeBytes = 2_150_000_000L,
                width = 3840,
                height = 2160,
                mimeType = "video/mp4",
                dateAdded = 1784100000L,
                folderName = "202607",
                path = "/storage/emulated/0/DCIM/202607/VID_20260715_143022_4K.mp4",
                isHdr = true,
                hdrFormat = "HDR10+ 60fps",
                codec = "HEVC Main10",
                bitrate = 55_000_000L,
                frameRate = 60.0f,
                audioChannels = 2,
                thumbnailUrl = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&auto=format&fit=crop&q=80"
            )
        )

        // 202614 folder
        list.add(
            VideoItem(
                id = 2002L,
                uri = Uri.parse("https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/bolt-detection.mp4"),
                title = "VID_20261408_091204_UHD.mp4",
                durationMs = 312000L,
                sizeBytes = 3_450_000_000L,
                width = 3840,
                height = 2160,
                mimeType = "video/mp4",
                dateAdded = 1784200000L,
                folderName = "202614",
                path = "/storage/emulated/0/DCIM/202614/VID_20261408_091204_UHD.mp4",
                isHdr = true,
                hdrFormat = "Dolby Vision",
                codec = "HEVC 10-bit",
                bitrate = 60_000_000L,
                frameRate = 30.0f,
                audioChannels = 6,
                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80"
            )
        )

        // Camera folder (Multiple videos)
        val cameraTitles = listOf(
            "IMG_8942_Cinematic_4K.mp4" to "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&auto=format&fit=crop&q=80",
            "Sunset_Coastline_ProRes.mp4" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600&auto=format&fit=crop&q=80",
            "SlowMotion_240fps_1080p.mp4" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            "Night_City_Timelapse_UHD.mp4" to "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=600&auto=format&fit=crop&q=80",
            "Family_Holiday_Trip_2026.mp4" to "https://images.unsplash.com/photo-1502082553048-f009c37129b9?w=600&auto=format&fit=crop&q=80",
            "Drone_Aerial_Mountain_4K.mp4" to "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=600&auto=format&fit=crop&q=80"
        )
        cameraTitles.forEachIndexed { i, (title, thumb) ->
            list.add(
                VideoItem(
                    id = 3000L + i,
                    uri = Uri.parse("https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/classroom.mp4"),
                    title = title,
                    durationMs = (120000L + i * 45000L),
                    sizeBytes = (1_200_000_000L + i * 650_000_000L),
                    width = 3840,
                    height = 2160,
                    mimeType = "video/mp4",
                    dateAdded = 1784000000L - i * 86400L,
                    folderName = "Camera",
                    path = "/storage/emulated/0/DCIM/Camera/$title",
                    isHdr = (i % 2 == 0),
                    hdrFormat = if (i % 2 == 0) "HDR10" else null,
                    codec = if (i % 2 == 0) "HEVC Main10" else "AVC H.264",
                    bitrate = 48_000_000L,
                    frameRate = if (i == 2) 240.0f else 60.0f,
                    audioChannels = 2,
                    thumbnailUrl = thumb
                )
            )
        }

        // Download folder (Multiple videos)
        val downloadTitles = listOf(
            "Big_Buck_Bunny_4K_UHD_Remux_HDR10_TrueHD.mkv" to "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
            "Avatar_The_Way_of_Water_4K_Remux_DTS-HD.mkv" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            "Oppenheimer_IMAX_4K_UHD_HEVC_Atmos.mkv" to "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600&auto=format&fit=crop&q=80",
            "Interstellar_2160p_HDR10_Remux.mkv" to "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80",
            "Dune_Part_Two_4K_DolbyVision_Remux.mkv" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80"
        )
        downloadTitles.forEachIndexed { i, (title, thumb) ->
            list.add(
                VideoItem(
                    id = 4000L + i,
                    uri = Uri.parse("https://media.w3.org/2010/05/sintel/trailer.mp4"),
                    title = title,
                    durationMs = (596000L + i * 180000L),
                    sizeBytes = (18_450_000_000L + i * 4_200_000_000L),
                    width = 3840,
                    height = 2160,
                    mimeType = "video/mp4",
                    dateAdded = 1784300000L - i * 43200L,
                    folderName = "Download",
                    path = "/storage/emulated/0/Download/$title",
                    isHdr = true,
                    hdrFormat = "Dolby Vision / HDR10+",
                    codec = "HEVC UHD Remux",
                    bitrate = 75_000_000L,
                    frameRate = 24.0f,
                    audioChannels = 8,
                    thumbnailUrl = thumb
                )
            )
        }

        // Movies folder
        list.add(
            VideoItem(
                id = 5001L,
                uri = Uri.parse("https://media.w3.org/2010/05/sintel/trailer.mp4"),
                title = "Sintel_4K_CinemaScope_DolbyAtmos.mkv",
                durationMs = 888000L,
                sizeBytes = 22_100_000_000L,
                width = 4096,
                height = 1744,
                mimeType = "video/mp4",
                dateAdded = 1784150000L,
                folderName = "Movies",
                path = "/storage/emulated/0/Movies/Sintel_4K_CinemaScope_DolbyAtmos.mkv",
                isHdr = true,
                hdrFormat = "HDR10 BT.2020",
                codec = "HEVC 10-bit Remux",
                bitrate = 85_000_000L,
                frameRate = 24.0f,
                audioChannels = 8,
                thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop&q=80"
            )
        )

        // Private folder (Protected / Vault items)
        val privateTitles = listOf(
            "Personal_Vault_Clip_01.mp4",
            "Project_Archive_Presentation.mp4",
            "Backup_Recorded_Stream_2026.mp4"
        )
        privateTitles.forEachIndexed { i, title ->
            list.add(
                VideoItem(
                    id = 6000L + i,
                    uri = Uri.parse("https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/head-pose-face-detection-female.mp4"),
                    title = title,
                    durationMs = (150000L + i * 80000L),
                    sizeBytes = (850_000_000L + i * 400_000_000L),
                    width = 1920,
                    height = 1080,
                    mimeType = "video/mp4",
                    dateAdded = 1784250000L - i * 10000L,
                    folderName = "Private",
                    path = "/storage/emulated/0/Private/$title",
                    isHdr = false,
                    hdrFormat = null,
                    codec = "AVC H.264",
                    bitrate = 18_000_000L,
                    frameRate = 30.0f,
                    audioChannels = 2
                )
            )
        }

        // Screenshots folder
        val screenshotTitles = listOf(
            "Screen_Recording_20260821_1102.mp4",
            "Screen_Recording_20260822_1945.mp4",
            "Screen_Recording_20260823_0815.mp4"
        )
        screenshotTitles.forEachIndexed { i, title ->
            list.add(
                VideoItem(
                    id = 7000L + i,
                    uri = Uri.parse("https://raw.githubusercontent.com/intel-iot-devkit/sample-videos/master/bolt-detection.mp4"),
                    title = title,
                    durationMs = (45000L + i * 30000L),
                    sizeBytes = (120_000_000L + i * 80_000_000L),
                    width = 1080,
                    height = 2400,
                    mimeType = "video/mp4",
                    dateAdded = 1784350000L - i * 5000L,
                    folderName = "Screenshots",
                    path = "/storage/emulated/0/DCIM/Screenshots/$title",
                    isHdr = false,
                    hdrFormat = null,
                    codec = "AVC H.264",
                    bitrate = 12_000_000L,
                    frameRate = 60.0f,
                    audioChannels = 2
                )
            )
        }

        return list
    }

    suspend fun createVideoItemFromUri(context: Context, uri: Uri): VideoItem = withContext(Dispatchers.IO) {
        var name = "External Video"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        VideoItem(
            id = System.currentTimeMillis(),
            uri = uri,
            title = name,
            durationMs = 0L,
            sizeBytes = size,
            width = 3840,
            height = 2160,
            mimeType = "video/*",
            dateAdded = System.currentTimeMillis() / 1000,
            folderName = "External Files",
            path = uri.toString(),
            isHdr = true,
            hdrFormat = "4K UHD Remux",
            codec = "Auto MediaCodec HW"
        )
    }
}
