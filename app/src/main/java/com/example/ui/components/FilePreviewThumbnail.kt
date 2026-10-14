package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.model.FileItem
import com.example.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A small, bounded preview for actual local media. Preview extraction is kept
 * off the main thread and uses the file timestamp/size in its key, so a new
 * download replaces an obsolete thumbnail instead of reusing it.
 */
@Composable
fun FilePreviewThumbnail(
    item: FileItem,
    fallbackIcon: ImageVector,
    fallbackBackground: Color,
    fallbackTint: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewable = (item.isApk || item.fileType in setOf(FileType.IMAGE, FileType.VIDEO, FileType.AUDIO)) &&
        !item.isPendingDownload
    val cacheKey = "${item.path}:${item.lastModified}:${item.sizeBytes}"
    val bitmap by produceState<Bitmap?>(
        initialValue = FilePreviewCache.get(cacheKey),
        key1 = cacheKey,
        key2 = previewable,
    ) {
        if (!previewable || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            FilePreviewCache.load(context, item)?.also { FilePreviewCache.put(cacheKey, it) }
        }
    }

    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(fallbackBackground),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = fallbackTint,
        )
        bitmap?.let { preview ->
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "Preview for ${item.displayName}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private object FilePreviewCache {
    private const val MAX_CACHE_KB = 6 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, value: Bitmap) = synchronized(cache) { cache.put(key, value) }

    fun load(context: android.content.Context, item: FileItem): Bitmap? = runCatching {
        when {
            item.isApk -> loadApkIcon(context, item.file)
            item.fileType == FileType.IMAGE -> loadImage(item.file)
            item.fileType == FileType.VIDEO -> loadVideo(item.file)
            item.fileType == FileType.AUDIO -> loadEmbeddedArtwork(item.file)
            else -> null
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun loadApkIcon(context: android.content.Context, file: File): Bitmap? {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val applicationInfo = packageInfo.applicationInfo ?: return null
        applicationInfo.sourceDir = file.absolutePath
        applicationInfo.publicSourceDir = file.absolutePath
        return applicationInfo.loadIcon(packageManager).toBitmap(width = 160, height = 160)
    }

    private fun loadImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 160)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    @Suppress("DEPRECATION")
    private fun loadVideo(file: File): Bitmap? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            ThumbnailUtils.createVideoThumbnail(file, Size(192, 108), null)
        else -> ThumbnailUtils.createVideoThumbnail(
            file.absolutePath,
            MediaStore.Video.Thumbnails.MINI_KIND,
        )
    }

    private fun loadEmbeddedArtwork(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } finally {
            retriever.release()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }
}
