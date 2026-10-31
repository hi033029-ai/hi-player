package com.example.ui.feedback

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/** Never use Throwable.message/server bodies as UI copy. Recovery text is app-owned. */
object UserFacingErrors {
    fun message(error: Throwable, recovery: String): String {
        if (error is CancellationException) throw error
        return when (error) {
            is UnknownHostException -> "You're offline. Check your connection and try again."
            is SocketTimeoutException -> "This is taking too long. Check your connection and try again."
            else -> recovery
        }
    }

    /** Log an event/type only: exception messages and stack traces can contain credentials. */
    fun report(event: String, error: Throwable, recovery: String): String {
        val copy = message(error, recovery)
        android.util.Log.w("HiPlayerUX", "$event (${error.javaClass.simpleName})")
        return copy
    }
}
