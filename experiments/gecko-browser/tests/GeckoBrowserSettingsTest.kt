package com.example.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OrbitBrowserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeckoBrowserSettingsTest {
    @Test fun savedPreferencesAreAppliedAndPersistAfterRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("orbit_browser", Context.MODE_PRIVATE).edit().clear().commit()
        val store = OrbitBrowserStore(context)
        var applied = Triple(false, 0, 0f)
        fun create() = GeckoBrowserSettings(store.darkMode, store.textZoom, store.popupBlock,
            { dark, zoom, scale -> applied = Triple(dark, zoom, scale) },
            { dark, zoom -> store.darkMode = dark; store.textZoom = zoom },
            { store.popupBlock = it })
        val settings = create()
        settings.applySystemScale(1.3f)
        assertEquals(Triple(false, 100, 1.3f), applied)
        settings.setAppearance(true, 150)
        settings.changePopupBlock(false)
        val restored = create()
        restored.applySystemScale(1.5f)
        assertEquals(Triple(true, 150, 1.5f), applied)
        assertTrue(restored.dark); assertEquals(150, restored.zoom); assertFalse(restored.popupBlock)
    }
    @Test fun failedApplyDoesNotSaveAndRestoresPriorAppearance() {
        val calls = mutableListOf<Pair<Boolean, Int>>()
        var saves = 0
        val settings = GeckoBrowserSettings(false, 100, true,
            { dark, zoom, _ -> calls += dark to zoom; if (zoom == 150) error("failure") },
            { _, _ -> saves++ }, {})
        settings.setAppearance(true, 150)
        assertTrue(settings.failed); assertEquals(0, saves); assertFalse(settings.dark); assertEquals(100, settings.zoom)
        assertEquals(listOf(true to 150, false to 100), calls)
    }
    @Test fun failureToSaveRollsBackRuntimeAndDoesNotChangeDisplayedValue() {
        val calls = mutableListOf<Int>()
        val settings = GeckoBrowserSettings(false, 100, true, { _, zoom, _ -> calls += zoom }, { _, _ -> error("storage") }, {})
        settings.setAppearance(true, 180)
        assertTrue(settings.failed); assertEquals(100, settings.zoom); assertFalse(settings.dark)
        assertEquals(listOf(180, 100), calls)
    }
    @Test fun normalizesScaleAndZoomAndSignalsPopupFailure() {
        var lastScale = 0f
        var lastZoom = 0
        val settings = GeckoBrowserSettings(false, 100, true, { _, zoom, scale -> lastZoom = zoom; lastScale = scale }, { _, _ -> }, { error("storage") })
        settings.applySystemScale(Float.NaN)
        settings.setAppearance(false, 900)
        assertEquals(1f, lastScale, 0f); assertEquals(200, lastZoom)
        settings.changePopupBlock(false)
        assertTrue(settings.popupBlock); assertTrue(settings.failed)
        settings.applySystemScale(1.2f)
        assertFalse(settings.failed); assertEquals(1.2f, lastScale, 0f)
    }
}
