package com.example.browser.gecko

/** Main-thread-only storage for approved transfers detached from their browser owner. */
internal class GeckoDetachedTransfers<T : Any> {
    private val values = mutableListOf<T>()
    fun retain(value: T) { values.add(value) }
    fun take(): T? = if (values.isEmpty()) null else values.removeAt(values.lastIndex)
    fun removeFirst(predicate: (T) -> Boolean): T? {
        val index = values.indexOfFirst(predicate)
        return if (index < 0) null else values.removeAt(index)
    }
}
