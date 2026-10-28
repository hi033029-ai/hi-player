package com.example.drive
import org.junit.Assert.*
import org.junit.Test
class GoogleDrivePolicyTest {
    @Test fun onlyExactHttpsDriveEndpointReceivesCredentials() {
        assertTrue(GoogleDrivePolicy.isMediaUrl(GoogleDrivePolicy.mediaUrl("abc_123-X")))
        listOf("http://www.googleapis.com/drive/v3/files/a?alt=media", "https://www.googleapis.com.evil.test/drive/v3/files/a?alt=media",
            "https://www.googleapis.com:444/drive/v3/files/a?alt=media", "https://www.googleapis.com/drive/v3/files/a?alt=media&access_token=secret",
            "https://www.googleapis.com/drive/v3/files/a/other?alt=media").forEach { assertFalse(it, GoogleDrivePolicy.isMediaUrl(it)) }
    }
    @Test fun redirectsRequireHttpsAndGoogleCdn() {
        assertTrue(GoogleDrivePolicy.safeRedirect("https://video.googleusercontent.com/media"))
        assertFalse(GoogleDrivePolicy.safeRedirect("https://googleusercontent.com.evil.test/media"))
        assertFalse(GoogleDrivePolicy.safeRedirect("http://video.googleusercontent.com/media"))
        assertFalse(GoogleDrivePolicy.safeRedirect("https://www.googleapis.com:444/media"))
    }
    @Test fun identifiersCannotInjectPathsOrUrls() {
        assertFalse(GoogleDrivePolicy.validFileId("../foo"))
        assertFalse(GoogleDrivePolicy.validFileId("abc?access_token=oops"))
        assertTrue(GoogleDrivePolicy.validFileId("1abc_DEF-567"))
    }
    @Test fun searchEscapesDriveQueryLiterals() { assertEquals("Alice\\'s \\\\ videos", GoogleDrivePolicy.escapeQuery("Alice's \\ videos")) }
}
