package com.example.recognition

import androidx.lifecycle.ViewModel
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
class SongRecognitionViewModel(private val appContext: Context) : ViewModel() {

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()
    private var recognitionJob: Job? = null
    private var selectedMode: SongRecognitionMode = SongRecognitionMode.PLAYING

    fun startListening(mode: SongRecognitionMode = selectedMode, sourceUri: Uri? = null) {
        if (_state.value is RecognitionState.Listening || _state.value is RecognitionState.Recognizing) return
        selectedMode = mode

        recognitionJob = viewModelScope.launch {
            _state.value = RecognitionState.Listening
            val sample = runCatching {
                if (sourceUri != null && sourceUri != Uri.EMPTY) {
                    LocalAudioExtractor.extract(appContext, sourceUri)
                } else {
                    val pcm = AudioCapture.recordPcm()
                    PcmSample(pcm, AudioCapture.SAMPLE_RATE, AudioCapture.CHANNELS)
                }
            }
                .getOrElse {
                    if (it is CancellationException) throw it
                    _state.value = RecognitionState.Error(it.message ?: "Recording failed")
                    return@launch
                }

            _state.value = RecognitionState.Recognizing
            val fingerprint = runCatching {
                ChromaprintNative.fingerprint(sample.samples, sample.sampleRate, sample.channels)
            }.getOrElse {
                if (it is CancellationException) throw it
                _state.value = RecognitionState.Error("Fingerprinting failed: ${it.message}")
                return@launch
            }

            if (fingerprint.isBlank()) {
                _state.value = RecognitionState.Error("Could not generate a fingerprint from that audio")
                return@launch
            }

            AcoustIdApi.lookup(fingerprint, sample.durationSeconds)
                .onSuccess { song ->
                    _state.value = if (song != null) RecognitionState.Found(song) else RecognitionState.NoMatch
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _state.value = RecognitionState.Error(it.message ?: "Lookup failed")
                }
        }
    }

    fun reset() {
        recognitionJob?.cancel()
        recognitionJob = null
        _state.value = RecognitionState.Idle
    }

    override fun onCleared() {
        recognitionJob?.cancel()
        recognitionJob = null
        super.onCleared()
    }
}
