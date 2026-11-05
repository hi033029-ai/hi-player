package com.example.ui.screens

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Play variant has no executable script update channel. */
internal object SignedBlockerUpdates {
    val revision = MutableStateFlow(0L).asStateFlow()
    val version = MutableStateFlow(0L).asStateFlow()
    fun script(): String? = null
    fun load(context: Context) = Unit
    fun update(context: Context, force: Boolean = false, cancelled: () -> Boolean = { false }): Boolean = false
    fun rollback(context: Context): Boolean = false
}
