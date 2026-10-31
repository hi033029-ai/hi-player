package com.example.ui.feedback

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingErrorsTest {
    @Test fun backendPayloadsNeverBecomeUserCopy() {
        val backend = IOException("HTTP 500 token=secret /private/database/path at StackTrace:42")
        val safe = UserFacingErrors.message(backend, "Couldn't load files. Tap Refresh to try again.")
        assertEquals("Couldn't load files. Tap Refresh to try again.", safe)
        assertFalse(safe.contains("secret"))
        assertFalse(safe.contains("500"))
    }
    @Test fun offlineHasAnActionableNextStep() {
        assertEquals("You're offline. Check your connection and try again.",
            UserFacingErrors.message(UnknownHostException("internal-host"), "Try again."))
    }
    @Test fun timeoutDoesNotLeakRequestDetails() {
        assertEquals("This is taking too long. Check your connection and try again.",
            UserFacingErrors.message(SocketTimeoutException("https://private?token=secret"), "Try again."))
    }
    @Test(expected = CancellationException::class) fun cancellationIsNotAnErrorScreen() {
        UserFacingErrors.message(CancellationException(), "Couldn't connect.")
    }
}
