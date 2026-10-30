package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import com.example.model.AudioItem
import com.example.ui.theme.LocalHiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@Composable
fun AudioTrackThumbnail(
    track: AudioItem,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val palette = LocalHiPalette.current
    val artworkKey = remember(track.path, track.uri, track.sizeBytes, track.albumArtUri) {
        com.example.util.AudioArtworkCache.key(track)
    }
    var embeddedBitmap by remember(artworkKey) {
        mutableStateOf<Bitmap?>(com.example.util.AudioArtworkCache.get(artworkKey))
    }

    // If artworkUrl exists (e.g. for web/demo tracks or remote streams), load with Coil
    if (!track.artworkUrl.isNullOrEmpty()) {
        AsyncImage(
            model = remember(context, track.artworkUrl) {
                ImageRequest.Builder(context).data(track.artworkUrl).crossfade(false).build()
            },
            contentDescription = "Artwork for ${track.title}",
            contentScale = contentScale,
            modifier = modifier
                .clip(shape)
                .background(palette.surfaceElevated)
        )
        return
    }

    LaunchedEffect(artworkKey) {
        if (embeddedBitmap == null) embeddedBitmap = com.example.util.AudioArtworkCache.load(context.applicationContext, track)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (embeddedBitmap != null) {
            Image(
                bitmap = embeddedBitmap!!.asImageBitmap(),
                contentDescription = "Artwork for ${track.title}",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Track-specific unique gradient & stylized audio artwork card
            val trackHash = (track.title + track.artist).hashCode().absoluteValue
            val baseGradients = remember(trackHash) {
                val colorList = listOf(
                    listOf(Color(0xFF6366F1), Color(0xFFA855F7)), // Indigo - Purple
                    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)), // Cyan - Blue
                    listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)), // Pink - Violet
                    listOf(Color(0xFF10B981), Color(0xFF06B6D4)), // Emerald - Cyan
                    listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), // Amber - Red
                    listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))  // Purple - Fuchsia
                )
                colorList[trackHash % colorList.size]
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(baseGradients)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Audio Track Icon",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    if (track.title.isNotEmpty()) {
                        Text(
                            text = track.title.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
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
