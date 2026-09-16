package com.example.recognition

object ChromaprintNative {
    init {
        System.loadLibrary("chromaprint_jni")
    }

    /** Returns a base64-encoded compressed fingerprint string, or "" on failure. */
    external fun fingerprint(pcmData: ShortArray, sampleRate: Int, numChannels: Int): String
}
