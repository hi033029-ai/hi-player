package com.example.ui.screens

/** Return to a surviving opener first; otherwise use most recently visited tabs. */
internal object BrowserTabBackPolicy {
    enum class BackAction { PAGE_HISTORY, HOME, EXIT_BROWSER }
    fun backAction(showingHome: Boolean, canGoBack: Boolean): BackAction = when {
        showingHome -> BackAction.EXIT_BROWSER
        canGoBack -> BackAction.PAGE_HISTORY
        else -> BackAction.HOME
    }
    // Explicit tab-close selection only. Back never uses MRU/opener tab history.
    fun target(current: Int, opener: Int?, openTabs: List<Int>, recent: List<Int>): Int? =
        opener?.takeIf { it != current && it in openTabs }
            ?: recent.asReversed().firstOrNull { it != current && it in openTabs }
            ?: openTabs.lastOrNull { it != current }
}
