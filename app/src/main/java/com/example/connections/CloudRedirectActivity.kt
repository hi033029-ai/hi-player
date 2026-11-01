package com.example.connections

import android.app.Activity
import android.os.Bundle

/** No callback code/PKCE verifier is stored in activity state or diagnostics. */
class CloudRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        val target = intent?.data
        intent?.data = null
        if (target != null) runCatching { PersonalCloud.acceptRedirect(target) }
        finish()
    }
    override fun onSaveInstanceState(outState: Bundle) { /* callback is single use, never restored */ }
}
