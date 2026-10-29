package com.example.cast

import org.junit.Assert.*
import org.junit.Test

class CastStreamPolicyTest {
    @Test fun directWebMediaCanBeOfferedToReceiver() {
        assertNull(CastStreamPolicy.rejection("https://media.example/video.mp4"))
        assertNull(CastStreamPolicy.rejection("http://192.168.1.5/movie.m3u8"))
    }
    @Test fun localAndLoopbackMediaCannotBeSentAsRemoteUrls() {
        assertNotNull(CastStreamPolicy.rejection("content://media/video/1"))
        assertNotNull(CastStreamPolicy.rejection("file:///storage/movie.mp4"))
        assertNotNull(CastStreamPolicy.rejection("http://localhost/movie.mp4"))
        assertNotNull(CastStreamPolicy.rejection("http://127.0.0.1/movie.mp4"))
    }
    @Test fun googleTokensAndUrlCredentialsNeverGoToTheTv() {
        assertNotNull(CastStreamPolicy.rejection("https://user:password@example.com/movie.mp4"))
        assertNotNull(CastStreamPolicy.rejection("https://example.com/movie.mp4?access_token=secret"))
        assertNotNull(CastStreamPolicy.rejection("https://example.com/movie.mp4?%69d_token=secret"))
        assertNotNull(CastStreamPolicy.rejection("https://www.googleapis.com/drive/v3/files/abc?alt=media"))
    }
}
