package com.example.ui.feedback

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/** Session memory only. Never put passwords/credential-bearing URLs in saved-state Bundles. */
class SessionDraftsViewModel : ViewModel() {
    val streamUrl = mutableStateOf("")
    val jellyfin = SourceSetupDraft()
    val webDav = SourceSetupDraft(defaultName = "WebDAV")

    override fun onCleared() {
        streamUrl.value = ""
        jellyfin.clear()
        webDav.clear()
        super.onCleared()
    }
}

class SourceSetupDraft(private val defaultName: String = "") {
    val url = mutableStateOf("")
    val username = mutableStateOf("")
    val password = mutableStateOf("")
    val name = mutableStateOf(defaultName)
    fun clear() {
        url.value = ""
        username.value = ""
        password.value = ""
        name.value = defaultName
    }
}
