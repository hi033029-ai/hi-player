package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.VideoItem
import com.example.ui.theme.LocalHiPalette
import com.example.util.VideoThumbnailCache

@Composable
fun VideoThumbnail(
    video: VideoItem,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val cacheKey = remember(video.id, video.uri, video.path) {
        val p = video.path
        if (!p.isNullOrEmpty()) p else video.uri.toString().ifEmpty { video.id.toString() }
    }

    var extractedBitmap by remember(cacheKey) {
        mutableStateOf<Bitmap?>(VideoThumbnailCache.get(cacheKey))
    }

    if (!video.thumbnailUrl.isNullOrEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(video.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Thumbnail for ${video.title}",
            contentScale = contentScale,
            modifier = modifier
                .clip(shape)
                .background(palette.surface)
        )
        return
    }

    LaunchedEffect(cacheKey) {
        if (extractedBitmap == null) {
            val loaded = VideoThumbnailCache.loadOrExtract(
                context = context,
                key = cacheKey,
                uri = video.uri,
                filePath = video.path
            )
            extractedBitmap = loaded
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.surface),
        contentAlignment = Alignment.Center
    ) {
        val bmp = extractedBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Thumbnail for ${video.title}",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.surfaceElevated)
            )
        }
    }
}
