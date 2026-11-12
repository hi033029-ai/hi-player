package com.example.browser.gecko

import org.junit.Assert.*
import org.junit.Test

class GeckoDetachedTransfersTest {
    @Test fun activeTransferCanBeReclaimedAndIsNotClosedByLateCompletion() {
        val detached = GeckoDetachedTransfers<Any>()
        val transfer = Any()
        detached.retain(transfer)
        assertSame(transfer, detached.take())
        assertNull(detached.removeFirst { it === transfer })
    }
    @Test fun completedDetachedTransferCanBeRemovedWithoutTouchingOthers() {
        val detached = GeckoDetachedTransfers<Any>()
        val first = Any(); val second = Any()
        detached.retain(first); detached.retain(second)
        assertSame(first, detached.removeFirst { it === first })
        assertSame(second, detached.take())
        assertNull(detached.take())
    }
}
