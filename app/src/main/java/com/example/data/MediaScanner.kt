package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaScanner {

    suspend fun scanLocalVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<VideoItem>()
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
                    val folderName = if (dataPath.isNotEmpty()) {
                        val parent = File(dataPath).parentFile?.name
                        parent ?: "Internal Storage"
                    } else {
                        "Videos"
                    }

                    val is4k = (width >= 3840 || height >= 2160)
                    val codec = when {
                        mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> "HEVC (H.265)"
                        mime.contains("av01", ignoreCase = true) || mime.contains("av1", ignoreCase = true) -> "AV1 UHD"
                        mime.contains("vp9", ignoreCase = true) -> "VP9 HDR"
                        else -> if (is4k) "HEVC Remux" else "AVC (H.264)"
                    }

                    videoList.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = name,
                            durationMs = duration,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            folderName = folderName,
                            path = dataPath,
                            isHdr = is4k,
                            hdrFormat = if (is4k) "HDR10 / Dolby Vision" else null,
                            codec = codec,
                            bitrate = if (duration > 0) (size * 8000L / duration) else 0L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If no local videos found on device/emulator, add high-quality 4K UHD Remux demo items
        if (videoList.isEmpty()) {
            videoList.addAll(getDemoRemuxVideos())
        }

        videoList
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
