package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.mediarouter.app.MediaRouteButton
import com.example.cast.CastStreamViewModel
import com.example.ui.screens.musicSelectedLabelColor
import com.example.ui.theme.LocalHiPalette
import com.google.android.gms.cast.framework.CastButtonFactory

/** Single purposeful window: enter media, play here, or explicitly send it to a Cast receiver. */
@Composable
internal fun StreamWindow(
    url: String, onUrlChange: (String) -> Unit,
    error: String?, resolving: Boolean,
    onPlay: () -> Unit, onDismiss: () -> Unit,
    onCastStarted: () -> Unit,
    cast: CastStreamViewModel = viewModel(),
) {
    val palette = LocalHiPalette.current
    val clipboard = LocalClipboardManager.current
    val castState by cast.state.collectAsState()
    val busy = resolving || castState.busy
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(28.dp), color = palette.surfaceElevated) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Stream & Cast", color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your link. Your screen.", color = palette.textSecondary, fontSize = 13.sp)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close stream window", tint = palette.textSecondary) }
                    }
                    OutlinedTextField(
                        value = url, onValueChange = onUrlChange, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
                        label = { Text("Media URL") }, placeholder = { Text("https://…") },
                        leadingIcon = { Icon(Icons.Default.Link, null, tint = palette.textSecondary) },
                        trailingIcon = { IconButton(enabled = !busy, onClick = { clipboard.getText()?.text?.let(onUrlChange) }) {
                            Icon(Icons.Default.ContentPaste, "Paste URL", tint = palette.textPrimary)
                        } },
                        isError = error != null, supportingText = { Text(error ?: "MP4 · HLS · DASH · public video pages") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.textPrimary, unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.primary, unfocusedBorderColor = palette.surfaceBorder, cursorColor = palette.primary,
                            focusedLabelColor = palette.primary, unfocusedLabelColor = palette.textSecondary),
                    )
                    Button(enabled = !busy && url.isNotBlank(), onClick = onPlay, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = musicSelectedLabelColor(palette.primary))) {
                        if (resolving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = musicSelectedLabelColor(palette.primary))
                        else Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp)); Text(if (resolving) "Finding playable media…" else "Play on this device")
                    }
                    HorizontalDivider(color = palette.surfaceBorder)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cast, null, tint = palette.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(castState.device ?: "Connect to a TV", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(if (castState.connected) "Google Cast connected" else "Cast-enabled TV or speaker · same Wi-Fi", color = palette.textSecondary, fontSize = 12.sp)
                        }
                        if (castState.available) CastDeviceButton(palette.isDark)
                    }
                    if (castState.connected) {
                        castState.title?.let { Text(it, color = palette.textSecondary, maxLines = 2, fontSize = 13.sp) }
                        OutlinedButton(enabled = !busy && url.isNotBlank(), onClick = { cast.load(url, onCastStarted) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Cast, null); Spacer(Modifier.width(8.dp)); Text("Cast this stream", color = palette.textPrimary) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = cast::togglePlayback, enabled = !busy) {
                                Icon(if (castState.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (castState.playing) "Pause on TV" else "Play on TV", tint = palette.textPrimary)
                            }
                            IconButton(onClick = cast::stop, enabled = !busy) { Icon(Icons.Default.Stop, "Stop on TV", tint = palette.textPrimary) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = cast::disconnect, enabled = !busy) { Text("Disconnect", color = palette.textPrimary) }
                        }
                    }
                    castState.message?.let { Text(it, color = palette.textSecondary, fontSize = 13.sp) }
                    Text("The TV opens the media link itself. Use a supported direct stream without account cookies or private headers. Local files, protected Drive media and webpage playback are not cast by this receiver.",
                        color = palette.textTertiary, fontSize = 12.sp)
                    if (castState.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.primary)
                }
            }
        }
    }
}

@Composable
private fun CastDeviceButton(dark: Boolean) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    if (failed) {
        Text("Cast setup unavailable", color = LocalHiPalette.current.textSecondary, fontSize = 11.sp)
    } else AndroidView(
        modifier = Modifier.size(48.dp),
        factory = {
            val themed = androidx.appcompat.view.ContextThemeWrapper(context,
                if (dark) androidx.appcompat.R.style.Theme_AppCompat else androidx.appcompat.R.style.Theme_AppCompat_Light)
            MediaRouteButton(themed).apply {
                contentDescription = "Choose a Google Cast device"
                setAlwaysVisible(true)
                runCatching { CastButtonFactory.setUpMediaRouteButton(themed, this) }
                    .onFailure { post { failed = true } }
            }
        },
    )
}
