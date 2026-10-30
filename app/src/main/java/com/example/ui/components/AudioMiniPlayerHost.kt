package com.example.ui.components

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.MusicViewModel
import com.example.ui.screens.WavyNowPlayingBottomSheet

/** Position ticks invalidate this small playback surface, not the app's navigation/library shell. */
@Composable
internal fun AudioMiniPlayerHost(musicViewModel: MusicViewModel, onOpenMusic: () -> Unit) {
    val track by musicViewModel.currentTrack.collectAsStateWithLifecycle()
    val playing by musicViewModel.isPlaying.collectAsStateWithLifecycle()
    val position by musicViewModel.currentPositionMs.collectAsStateWithLifecycle()
    val duration by musicViewModel.durationMs.collectAsStateWithLifecycle()
    val preset by musicViewModel.equalizerPreset.collectAsStateWithLifecycle()
    val subtitle by musicViewModel.activeSubtitle.collectAsStateWithLifecycle()
    val item = track ?: return
    WavyNowPlayingBottomSheet(
        track = item, isPlaying = playing, currentPos = position,
        duration = duration.takeIf { it > 0 } ?: item.durationMs,
        eqPreset = preset, activeSubtitle = subtitle,
        onTogglePlay = musicViewModel::togglePlayPause,
        onNext = musicViewModel::playNext, onPrevious = musicViewModel::playPrevious,
        onSeek = musicViewModel::seekTo, onOpenEq = onOpenMusic,
        onOpenSubtitleSearch = onOpenMusic, onCancel = musicViewModel::stopTrack,
        onExpandFullScreen = musicViewModel::openFullScreenPlayer,
    )
}
