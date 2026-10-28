package com.example.auth
import org.junit.Assert.*
import org.junit.Test
class GoogleSignInConfigurationTest {
    @Test fun noFakeOrMissingClientIdsAreAccepted() {
        assertFalse(GoogleSignInConfiguration.validClientId(""))
        assertFalse(GoogleSignInConfiguration.validClientId("YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"))
        assertFalse(GoogleSignInConfiguration.validClientId("name@example.test"))
    }
    @Test fun oauthIdentifierFormatIsRequiredButDoesNotClaimProvisioning() {
        assertTrue(GoogleSignInConfiguration.validClientId("123456789012-testformatonly.apps.googleusercontent.com"))
        assertFalse(GoogleSignInConfiguration.validClientId("123456789012-testformatonly.apps.googleusercontent.com.evil.test"))
    }
}
