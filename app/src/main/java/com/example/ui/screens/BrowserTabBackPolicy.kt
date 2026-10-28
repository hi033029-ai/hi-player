package com.example.ui.screens

/** Return to a surviving opener first; otherwise use most recently visited tabs. */
internal object BrowserTabBackPolicy {
    fun target(current: Int, opener: Int?, openTabs: List<Int>, recent: List<Int>): Int? =
        opener?.takeIf { it != current && it in openTabs }
            ?: recent.asReversed().firstOrNull { it != current && it in openTabs }
            ?: openTabs.lastOrNull { it != current }
}
