package com.example.player

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteStreamHeadersRegistryTest {
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
