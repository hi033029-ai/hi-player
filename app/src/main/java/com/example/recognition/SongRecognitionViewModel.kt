package com.example.recognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RecognitionState {
    object Idle : RecognitionState()
    object Listening : RecognitionState()
    object Recognizing : RecognitionState()
    data class Found(val song: RecognizedSong) : RecognitionState()
    object NoMatch : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}

/**
 * AcoustID only — no AudD fallback. That means remix/slowed+reverb/pitch-shifted
 * tracks generally will NOT be recognized (see the earlier explanation: Chromaprint's
 * landmark fingerprinting matches against the original recording's fingerprint, and
 * those edits are acoustically a different signal). That's the real cost of staying
 * 100% free-forever with no trial/paid tier anywhere in the pipeline — not a bug,
 * just the honest tradeoff of this path.
 */
class SongRecognitionViewModel : ViewModel() {

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    fun startListening() {
        if (_state.value is RecognitionState.Listening || _state.value is RecognitionState.Recognizing) return

        viewModelScope.launch {
            _state.value = RecognitionState.Listening
            val pcm = runCatching { AudioCapture.recordPcm() }
                .getOrElse {
                    _state.value = RecognitionState.Error(it.message ?: "Recording failed")
                    return@launch
                }

            _state.value = RecognitionState.Recognizing
            val fingerprint = runCatching {
                ChromaprintNative.fingerprint(pcm, AudioCapture.SAMPLE_RATE, AudioCapture.CHANNELS)
            }.getOrElse {
                _state.value = RecognitionState.Error("Fingerprinting failed: ${it.message}")
                return@launch
            }

            if (fingerprint.isBlank()) {
                _state.value = RecognitionState.Error("Could not generate a fingerprint from that audio")
                return@launch
            }

            val durationSeconds = pcm.size / AudioCapture.SAMPLE_RATE
            AcoustIdApi.lookup(fingerprint, durationSeconds)
                .onSuccess { song ->
                    _state.value = if (song != null) RecognitionState.Found(song) else RecognitionState.NoMatch
                }
                .onFailure {
                    _state.value = RecognitionState.Error(it.message ?: "Lookup failed")
                }
        }
    }

    fun reset() {
        _state.value = RecognitionState.Idle
    }
}
