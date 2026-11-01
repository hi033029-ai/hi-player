package com.example.data

import org.junit.Assert.*
import org.junit.Test

class NetworkMediaPolicyTest {
    @Test fun normalizesExplicitServerAndFolderAddresses() {
        assertEquals("https://example.com/jellyfin", NetworkMediaPolicy.server(" https://example.com/jellyfin/ "))
        assertEquals("http://192.168.1.5:8096/", NetworkMediaPolicy.folder("http://192.168.1.5:8096"))
        assertEquals("https://example.com/media/", NetworkMediaPolicy.folder("https://example.com/media/"))
    }
    @Test fun rejectsCredentialsAndAmbiguousConfiguration() {
        listOf("file:///media", "javascript:alert(1)", "https://user:secret@example.com/", "https://example.com/?token=x",
            "https://example.com/#session", "https://example.com/a/../b", "https://example.com/a/%2e%2e/b",
            "https://example.com/a/%252e%252e/b", "https://example.com/a%2fb", "https://example.com:0/",
            "https://example.com:65536/").forEach { assertTrue(it.substringBefore('?'), runCatching { NetworkMediaPolicy.server(it) }.isFailure) }
    }
    @Test fun originsAreExactNotHostnameSuffixes() {
        assertTrue(NetworkMediaPolicy.sameOrigin("https://server.test/", "https://SERVER.test:443/video"))
        assertFalse(NetworkMediaPolicy.sameOrigin("https://server.test/", "https://server.test.evil/video"))
        assertFalse(NetworkMediaPolicy.sameOrigin("https://server.test/", "http://server.test/video"))
        assertFalse(NetworkMediaPolicy.sameOrigin("https://server.test/", "https://server.test:8443/video"))
        assertFalse(NetworkMediaPolicy.sameOrigin("https://server.test/", "https://user:secret@server.test/video"))
        assertFalse(NetworkMediaPolicy.sameOrigin("file:/a", "file:/b"))
    }
    @Test fun davFolderScopeRequiresBoundaryAndNoTraversal() {
        val root = "https://server.test/dav/videos/"
        assertTrue(NetworkMediaPolicy.within(root, root))
        assertTrue(NetworkMediaPolicy.within(root, "https://server.test/dav/videos"))
        assertTrue(NetworkMediaPolicy.within(root, "https://server.test/dav/videos/sub/movie%20name.mp4"))
        listOf("https://server.test/dav/videos-other/movie.mp4", "https://server.test/dav/videos/../private/file",
            "https://server.test/dav/videos/%2e%2e/private", "https://server.test/dav/videos/%252e%252e/private",
            "https://elsewhere.test/dav/videos/file", "https://server.test/dav/videos/file?token=x").forEach {
            assertFalse(NetworkMediaPolicy.within(root, it))
        }
    }
}
