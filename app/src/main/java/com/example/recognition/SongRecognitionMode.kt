package com.example.recognition

enum class SongRecognitionMode(
    val title: String,
    val description: String,
) {
    PLAYING("Song playing", "Identify music playing around you"),
    HUMMING("Hum or sing", "Try the melody with your voice"),
    BACKGROUND("Background audio", "Listen through the microphone"),
}
