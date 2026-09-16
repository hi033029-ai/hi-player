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
            val samples = runCatching {
                if (sourceUri != null && sourceUri != Uri.EMPTY) {
                    // TikTok-style clips often begin with speech or a sound effect.
                    // Try overlapping windows so recognition is not decided by the
                    // first ten seconds alone.
                    listOf(0L, 5L, 10L).mapNotNull { offset ->
                        runCatching { LocalAudioExtractor.extract(appContext, sourceUri, offset) }
                            .getOrElse {
                                if (it is CancellationException) throw it
                                null
                            }
                    }
                } else {
                    val pcm = AudioCapture.recordPcm()
                    listOf(PcmSample(pcm, AudioCapture.SAMPLE_RATE, AudioCapture.CHANNELS))
                }
            }.getOrElse {
                    if (it is CancellationException) throw it
                    _state.value = RecognitionState.Error(it.message ?: "Recording failed")
                    return@launch
                }

            _state.value = RecognitionState.Recognizing
            var recognized: RecognizedSong? = null
            var lastFingerprintError: Throwable? = null
            var lastLookupError: Throwable? = null
            var hadSuccessfulLookup = false
            for (sample in samples) {
                val fingerprint = runCatching {
                    ChromaprintNative.fingerprint(sample.samples, sample.sampleRate, sample.channels)
                }.getOrElse {
                    lastFingerprintError = it
                    ""
                }
                if (fingerprint.isBlank()) continue
                val result = AcoustIdApi.lookup(fingerprint, sample.durationSeconds)
                result.onSuccess { song ->
                    hadSuccessfulLookup = true
                    if (recognized == null) recognized = song
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    lastLookupError = error
                }
                if (recognized != null) break
            }

            if (recognized == null && lastFingerprintError != null && samples.isEmpty()) {
                _state.value = RecognitionState.Error("Fingerprinting failed: ${lastFingerprintError?.message}")
                return@launch
            }
            if (recognized == null && !hadSuccessfulLookup && lastLookupError != null) {
                _state.value = RecognitionState.Error(lastLookupError?.message ?: "AcoustID lookup failed")
                return@launch
            }
            _state.value = recognized?.let { RecognitionState.Found(it) } ?: RecognitionState.NoMatch
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
