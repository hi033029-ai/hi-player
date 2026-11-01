package com.example.player

import android.net.Uri
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteStreamHeadersRegistryTest {
    private val root = "https://network-test.invalid/library"
    @After fun cleanup() { RemoteStreamHeadersRegistry.clear("https://network-test.invalid") }
    @Test fun revocationRemovesLegacyChildScopesWithoutOtherFolders() {
        RemoteStreamHeadersRegistry.register(root, mapOf("X-Emby-Token" to "test-root"))
        RemoteStreamHeadersRegistry.register("$root/Videos/one", mapOf("X-Emby-Token" to "test-child"))
        RemoteStreamHeadersRegistry.register("https://network-test.invalid/other", mapOf("Authorization" to "test-other"))
        RemoteStreamHeadersRegistry.clear(root)
        assertTrue(RemoteStreamHeadersRegistry.headersFor(Uri.parse("$root/Videos/one")).isEmpty())
        assertTrue(RemoteStreamHeadersRegistry.headersFor(Uri.parse("$root/Videos/two")).isEmpty())
        assertEquals("test-other", RemoteStreamHeadersRegistry.headersFor(Uri.parse("https://network-test.invalid/other/file"))["Authorization"])
    }
    @Test fun credentialsStayWithinExactOriginAndPath() {
        RemoteStreamHeadersRegistry.register(root, mapOf("Authorization" to "test-only"))
        assertEquals("test-only", RemoteStreamHeadersRegistry.headersFor(Uri.parse("$root/file"))["Authorization"])
        listOf("http://network-test.invalid/library/file", "https://network-test.invalid.evil/library/file",
            "https://network-test.invalid:8443/library/file", "https://network-test.invalid/library-other/file",
            "$root/../private/file", "https://user:pass@network-test.invalid/library/file").forEach {
            assertTrue(RemoteStreamHeadersRegistry.headersFor(Uri.parse(it)).isEmpty())
        }
    }
    @Test
    fun `applies a scoped source header to child stream paths only`() {
        RemoteStreamHeadersRegistry.clear("https://media.example.test/library")
        RemoteStreamHeadersRegistry.register(
            "https://media.example.test/library",
            mapOf("Authorization" to "Basic test")
        )

        assertEquals(
            "Basic test",
            RemoteStreamHeadersRegistry.headersFor(Uri.parse("https://media.example.test/library/film.mkv"))["Authorization"]
        )
        assertTrue(
            RemoteStreamHeadersRegistry.headersFor(Uri.parse("https://media.example.test/other/film.mkv")).isEmpty()
        )
        RemoteStreamHeadersRegistry.clear("https://media.example.test/library")
    }
}
