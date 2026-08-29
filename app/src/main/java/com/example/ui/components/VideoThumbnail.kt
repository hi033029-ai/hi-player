package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.VideoItem
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@Composable
fun VideoThumbnail(
    video: VideoItem,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    var extractedBitmap by remember(video.id, video.uri, video.path) { mutableStateOf<Bitmap?>(null) }
    var attemptedExtraction by remember(video.id, video.uri, video.path) { mutableStateOf(false) }

    // 1. If explicitly provided a thumbnail URL (e.g., demo/sample remote videos), load via Coil
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

    // 2. Extract actual video frame thumbnail for local / downloaded / content URI files
    LaunchedEffect(video.id, video.uri, video.path) {
        if (!attemptedExtraction) {
            withContext(Dispatchers.IO) {
                var bitmap: Bitmap? = null

                // Attempt 1: ContentResolver.loadThumbnail on Android Q+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && video.uri != Uri.EMPTY && video.uri.scheme == "content") {
                    try {
                        bitmap = context.contentResolver.loadThumbnail(video.uri, Size(380, 240), null)
                    } catch (_: Exception) {}
                }

                // Attempt 2: MediaMetadataRetriever on Uri
                if (bitmap == null && video.uri != Uri.EMPTY) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, video.uri)
                        bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    } catch (_: Exception) {
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }

                // Attempt 3: MediaMetadataRetriever on file path
                if (bitmap == null && video.path.isNotEmpty()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(video.path)
                        bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    } catch (_: Exception) {
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }

                extractedBitmap = bitmap
                attemptedExtraction = true
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.surface),
        contentAlignment = Alignment.Center
    ) {
        if (extractedBitmap != null) {
            Image(
                bitmap = extractedBitmap!!.asImageBitmap(),
                contentDescription = "Thumbnail for ${video.title}",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Dynamic video poster fallback card with cinematic gradients
            val titleHash = (video.title + video.folderName).hashCode().absoluteValue
            val gradients = remember(titleHash) {
                val list = listOf(
                    listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155)), // Dark slate
                    listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA)), // Deep Indigo
                    listOf(Color(0xFF022C22), Color(0xFF064E3B), Color(0xFF047857)), // Emerald Dark
                    listOf(Color(0xFF360033), Color(0xFF0B8793), Color(0xFF1E293B)), // Deep Cyan/Fuchsia
                    listOf(Color(0xFF2A0845), Color(0xFF6441A5), Color(0xFF1E293B))  // Violet Night
                )
                list[titleHash % list.size]
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradients)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Video Poster Icon",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    )
                    if (video.title.isNotEmpty()) {
                        Text(
                            text = video.title.take(3).uppercase(),
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
